import logging
import threading
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse

from ai_service import ingest
from ai_service.analysis import AnalysisRequest, AnalysisResult, Analyzer, InvalidAnalysis
from ai_service.config import Settings
from ai_service.corpus import load_rubric
from ai_service.providers import chat_model, query_embedder
from ai_service.store import VectorStore

log = logging.getLogger("ai_service")


def problem(status: int, title: str, detail: str, kind: str) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        media_type="application/problem+json",
        content={"type": f"https://mentoria.app/errors/{kind}", "title": title, "status": status, "detail": detail},
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


def build_analyzer(settings: Settings) -> Analyzer | None:
    if not settings.api_key:
        log.warning("Chave do provedor %s não definida: a análise responderá 503.", settings.provider)
        return None
    log.info("Provedor: %s | modelo: %s | embeddings: %s", settings.provider, settings.llm_model,
             settings.embedding_model)
    store = VectorStore(settings.database_url, settings.embedding_dimensions)
    try:
        store.ensure_schema()
    except Exception:
        log.exception("Não foi possível preparar o banco vetorial.")
    if settings.auto_ingest:
        threading.Thread(target=_auto_ingest, args=(settings, store), daemon=True).start()
    return Analyzer(
        embed_query=query_embedder(settings),
        retriever=store,
        llm=chat_model(settings),
        rubric=load_rubric(settings.corpus_dir),
        top_k=settings.top_k,
        essay_k=settings.essay_k,
    )


def _auto_ingest(settings: Settings, store: VectorStore) -> None:
    try:
        report = ingest.run(settings, store)
        log.info("Corpus sincronizado: %d ingeridos, %d sem mudança, %d removidos.",
                 len(report.ingested), len(report.unchanged), len(report.removed))
    except Exception:
        log.exception("Falha ao sincronizar o corpus. Rode a ingestão manualmente.")


def create_app(analyzer: Analyzer | None = None, settings: Settings | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if app.state.analyzer is None:
            app.state.analyzer = build_analyzer(settings or Settings.from_env())
        yield

    app = FastAPI(
        title="MentorIA AI Service",
        description="Análise de redações do ENEM com RAG sobre pgvector",
        version="1.0.0",
        lifespan=lifespan,
    )
    app.state.analyzer = analyzer

    @app.get("/health")
    def health_check(request: Request):
        return {
            "status": "healthy",
            "service": "ai-service",
            "analysis": "ready" if request.app.state.analyzer else "disabled",
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

    return app


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
app = create_app()
