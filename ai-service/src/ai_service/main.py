import logging
import threading
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse

from ai_service import ingest
from ai_service.analysis import AnalysisRequest, AnalysisResult, Analyzer, InvalidAnalysis
from ai_service.catalog import load_catalog
from ai_service.config import Settings
from ai_service.corpus import load_rubric
from ai_service.providers import chat_model, document_embedder, query_embedder
from ai_service.store import VectorStore
from ai_service.study_plan import (REJECTION_MESSAGE, Intake, InvalidPlan, PlanDraft, PlanRejected,
                                   StudyPlanner, StudyPlanRequest, StudyPlanSuggestion)

log = logging.getLogger("ai_service")


def problem(status: int, title: str, detail: str, kind: str, **extra: str) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        media_type="application/problem+json",
        content={"type": f"https://mentoria.app/errors/{kind}", "title": title, "status": status, "detail": detail,
                 **extra},
    )


def provider_message(error: Exception) -> str:
    body = getattr(error, "body", None)
    if isinstance(body, list) and body:
        body = body[0]
    if isinstance(body, dict):
        detail = body.get("error", body)
        message = detail.get("message") if isinstance(detail, dict) else None
        if message:
            return str(message)[:300]
    return f"{type(error).__name__}: {str(error)[:300]}"


def build_services(settings: Settings) -> tuple[Analyzer | None, StudyPlanner | None]:
    if not settings.api_key:
        log.warning("Chave do provedor %s não definida: a análise responderá 503.", settings.provider)
        return None, None
    log.info("Provedor: %s | modelo: %s | embeddings: %s", settings.provider, settings.llm_model,
             settings.embedding_model)
    store = VectorStore(settings.database_url, settings.embedding_dimensions)
    try:
        store.ensure_schema()
    except Exception:
        log.exception("Não foi possível preparar o banco vetorial.")
    if settings.auto_ingest:
        threading.Thread(target=_auto_ingest, args=(settings, store), daemon=True).start()
    analyzer = Analyzer(
        embed_query=query_embedder(settings),
        retriever=store,
        llm=chat_model(settings),
        rubric=load_rubric(settings.corpus_dir),
        top_k=settings.top_k,
        essay_k=settings.essay_k,
    )
    planner = StudyPlanner(
        intake_llm=chat_model(settings, Intake),
        planner_llm=chat_model(settings, PlanDraft),
        embed_documents=document_embedder(settings, attempts=2),
        retriever=store,
        catalog=load_catalog(settings.catalog_dir),
    )
    return analyzer, planner


def _auto_ingest(settings: Settings, store: VectorStore) -> None:
    try:
        catalog = ingest.run_catalog(settings, store)
        log.info("Catálogo de atividades sincronizado: %d vetorizadas, %d sem mudança, %d removidas.",
                 catalog.embedded, catalog.unchanged, catalog.removed)
    except Exception:
        log.exception("Falha ao sincronizar o catálogo de atividades.")
    try:
        report = ingest.run(settings, store)
        log.info("Corpus sincronizado: %d ingeridos, %d sem mudança, %d removidos.",
                 len(report.ingested), len(report.unchanged), len(report.removed))
    except Exception:
        log.exception("Falha ao sincronizar o corpus. Rode a ingestão manualmente.")


def create_app(analyzer: Analyzer | None = None, settings: Settings | None = None,
               planner: StudyPlanner | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if app.state.analyzer is None and app.state.planner is None:
            app.state.analyzer, app.state.planner = build_services(settings or Settings.from_env())
        yield

    app = FastAPI(
        title="MentorIA AI Service",
        description="Correção de redações do ENEM e planos de estudo com RAG sobre pgvector",
        version="1.0.0",
        lifespan=lifespan,
    )
    app.state.analyzer = analyzer
    app.state.planner = planner

    @app.get("/health")
    def health_check(request: Request):
        return {
            "status": "healthy",
            "service": "ai-service",
            "analysis": "ready" if request.app.state.analyzer else "disabled",
            "studyPlans": "ready" if request.app.state.planner else "disabled",
        }

    @app.post("/api/v1/analysis", response_model=AnalysisResult)
    async def analyze(body: AnalysisRequest, request: Request):
        current: Analyzer | None = request.app.state.analyzer
        if current is None:
            return problem(503, "Serviço de IA indisponível",
                           "A chave do provedor de IA não está configurada no AI Service.", "ai-unavailable")
        try:
            return await run_in_threadpool(current.analyze, body)
        except InvalidAnalysis as e:
            log.warning("Análise inválida para a redação %s: %s", body.essayId, e)
            return problem(502, "Análise inválida", str(e), "ai-invalid-analysis")
        except Exception as error:
            log.exception("Falha ao analisar a redação %s", body.essayId)
            return problem(503, "Serviço de IA indisponível",
                           f"O modelo de linguagem não respondeu: {provider_message(error)}", "ai-unavailable")

    @app.post("/api/v1/study-plans/suggestions", response_model=StudyPlanSuggestion)
    async def suggest_study_plan(body: StudyPlanRequest, request: Request):
        current: StudyPlanner | None = request.app.state.planner
        if current is None:
            return problem(503, "Serviço de IA indisponível",
                           "A chave do provedor de IA não está configurada no AI Service.", "ai-unavailable")
        try:
            return await run_in_threadpool(current.suggest, body)
        except PlanRejected as rejected:
            log.info("Pedido de plano recusado para o aluno %s: %s", body.studentId, rejected.reason)
            return problem(422, "Pedido fora do escopo", REJECTION_MESSAGE, "study-plan-out-of-scope",
                           reason=rejected.reason)
        except InvalidPlan as e:
            log.warning("Plano inválido para o aluno %s: %s", body.studentId, e)
            return problem(502, "Plano inválido", str(e), "ai-invalid-plan")
        except Exception as error:
            log.exception("Falha ao montar o plano do aluno %s", body.studentId)
            return problem(503, "Serviço de IA indisponível",
                           f"O modelo de linguagem não respondeu: {provider_message(error)}", "ai-unavailable")

    return app


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
app = create_app()
