import argparse
import json
import sys
import time
import uuid
from collections import Counter
from pathlib import Path

from ai_service.catalog import load_catalog
from ai_service.config import Settings
from ai_service.providers import chat_model, document_embedder
from ai_service.store import VectorStore
from ai_service.study_plan import (Intake, PlanDraft, PlanRejected, ProgressInput, StudyPlanner,
                                   StudyPlanRequest)

DEFAULT_FILE = Path(__file__).resolve().parents[2] / "eval" / "study-plan-requests.jsonl"
SAMPLE_PROGRESS = {"averageByCompetency": [{"id": 1, "average": 150}, {"id": 2, "average": 130},
                                           {"id": 3, "average": 120}, {"id": 4, "average": 140},
                                           {"id": 5, "average": 80}], "weakestCompetency": 5}
SAMPLE_AREA_LEVELS = {"matematica": "basico", "natureza": "intermediario", "humanas": "intermediario",
                      "linguagens": "avancado"}


def classify(planner: StudyPlanner, text: str) -> tuple[str, str]:
    request = StudyPlanRequest(studentId=uuid.uuid4(), message=text,
                               progress=ProgressInput(**SAMPLE_PROGRESS), areaLevels=SAMPLE_AREA_LEVELS)
    try:
        suggestion = planner.suggest(request)
    except PlanRejected as rejected:
        return "recusar", rejected.reason
    except Exception as error:
        return "erro", f"{type(error).__name__}: {str(error)[:120]}"
    return "aceitar", f"{len(suggestion.tasks)} atividades do catálogo"


def main() -> int:
    parser = argparse.ArgumentParser(description="Mede se o plano de estudos aceita pedidos legítimos e recusa ataques.")
    parser.add_argument("--file", type=Path, default=DEFAULT_FILE)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--delay", type=float, default=None)
    args = parser.parse_args()

    settings = Settings.from_env()
    if not settings.api_key:
        print("Defina a chave do provedor (OPENAI_API_KEY ou GEMINI_API_KEY) para rodar a avaliação.", file=sys.stderr)
        return 1
    delay = args.delay if args.delay is not None else (6.0 if settings.provider == "gemini" else 0.0)
    store = VectorStore(settings.database_url, settings.embedding_dimensions)
    planner = StudyPlanner(chat_model(settings, Intake), chat_model(settings, PlanDraft),
                           document_embedder(settings, attempts=2), store, load_catalog(settings.catalog_dir))

    with args.file.open(encoding="utf-8") as handle:
        cases = [json.loads(line) for line in handle if line.strip()]
    if args.limit:
        cases = cases[:args.limit]

    results = Counter()
    failures = []
    for index, case in enumerate(cases, start=1):
        if delay and index > 1:
            time.sleep(delay)
        outcome, detail = classify(planner, case["text"])
        ok = outcome == case["expected"]
        results[(case["expected"], ok)] += 1
        mark = "ok " if ok else "ERRO"
        print(f"[{mark}] {case['kind']:<13} esperado={case['expected']:<8} obtido={outcome:<8} {detail}", file=sys.stderr)
        if not ok:
            failures.append(case)

    legit = results[("aceitar", True)] + results[("aceitar", False)]
    attacks = results[("recusar", True)] + results[("recusar", False)]
    print(f"Pedidos legítimos aceitos: {results[('aceitar', True)]}/{legit}")
    print(f"Ataques e pedidos fora do tema recusados: {results[('recusar', True)]}/{attacks}")
    for case in failures:
        print(f"  falhou ({case['kind']}): {case['text'][:90]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
