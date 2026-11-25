import argparse
import json
import sys
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from ai_service.analysis import AnalysisRequest, Analyzer
from ai_service.config import Settings
from ai_service.corpus import load_rubric
from ai_service.providers import chat_model, query_embedder
from ai_service.store import VectorStore

DEFAULT_EVAL_FILE = Path(__file__).resolve().parents[2] / "eval" / "essay-br-test.jsonl"


@dataclass(frozen=True)
class Metrics:
    count: int
    mae_by_competency: tuple[float, ...]
    mae_total: float
    exact: float
    adjacent: float


def compute_metrics(expected: Sequence[Sequence[int]], predicted: Sequence[Sequence[int]]) -> Metrics:
    pairs = list(zip(expected, predicted, strict=True))
    if not pairs:
        return Metrics(0, (0.0,) * 5, 0.0, 0.0, 0.0)
    n = len(pairs)
    diffs = [[abs(e - p) for e, p in zip(exp, pred, strict=True)] for exp, pred in pairs]
    flat = [d for row in diffs for d in row]
    return Metrics(
        count=n,
        mae_by_competency=tuple(sum(row[c] for row in diffs) / n for c in range(5)),
        mae_total=sum(abs(sum(exp) - sum(pred)) for exp, pred in pairs) / n,
        exact=sum(d == 0 for d in flat) / len(flat),
        adjacent=sum(d <= 40 for d in flat) / len(flat),
    )


def load_eval_set(path: Path, limit: int) -> list[dict]:
    with path.open(encoding="utf-8") as handle:
        essays = [json.loads(line) for line in handle if line.strip()]
    return essays[:limit]


def run(analyzer: Analyzer, essays: list[dict], use_rag: bool,
        delay: float = 0.0) -> tuple[list[list[int]], list[list[int]]]:
    expected, predicted = [], []
    for index, essay in enumerate(essays, start=1):
        if delay and index > 1:
            time.sleep(delay)
        request = AnalysisRequest(studentId=uuid.uuid4(), essayId=uuid.uuid4(),
                                  theme=essay["theme"] or "Tema livre", text=essay["text"])
        try:
            result = analyzer.analyze(request, use_rag=use_rag)
        except Exception as error:
            print(f"  [{index}/{len(essays)}] falhou: {error}", file=sys.stderr)
            continue
        expected.append(list(essay["scores"]))
        predicted.append([c.score for c in result.competencies])
        print(f"  [{index}/{len(essays)}] humano={sum(essay['scores'])} ia={sum(predicted[-1])}", file=sys.stderr)
    return expected, predicted


def report(label: str, metrics: Metrics) -> str:
    by_c = " ".join(f"C{i}={v:5.1f}" for i, v in enumerate(metrics.mae_by_competency, start=1))
    return (f"{label:<9} n={metrics.count:<4} erro médio por competência: {by_c} | "
            f"erro médio na nota total: {metrics.mae_total:6.1f} | "
            f"nota exata: {metrics.exact:5.1%} | até 40 pontos: {metrics.adjacent:5.1%}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Compara as notas da IA com as notas humanas do conjunto de avaliação.")
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--file", type=Path, default=DEFAULT_EVAL_FILE)
    parser.add_argument("--no-baseline", action="store_true", help="não roda a comparação sem RAG")
    parser.add_argument("--delay", type=float, default=None,
                        help="segundos entre chamadas (padrão: 6 no Gemini, 0 nos demais)")
    args = parser.parse_args()

    settings = Settings.from_env()
    if not settings.api_key:
        print("Defina a chave do provedor (OPENAI_API_KEY ou GEMINI_API_KEY) para rodar a avaliação.",
              file=sys.stderr)
        return 1
    delay = args.delay if args.delay is not None else (6.0 if settings.provider == "gemini" else 0.0)
    analyzer = Analyzer(
        embed_query=query_embedder(settings),
        retriever=VectorStore(settings.database_url, settings.embedding_dimensions),
        llm=chat_model(settings),
        rubric=load_rubric(settings.corpus_dir),
        top_k=settings.top_k,
        essay_k=settings.essay_k,
    )
    essays = load_eval_set(args.file, args.limit)
    lines = []
    print(f"Com RAG ({len(essays)} redações)...", file=sys.stderr)
    print(f"Provedor: {settings.provider} | modelo: {settings.llm_model}", file=sys.stderr)
    lines.append(report("com RAG", compute_metrics(*run(analyzer, essays, use_rag=True, delay=delay))))
    if not args.no_baseline:
        print(f"Sem RAG ({len(essays)} redações)...", file=sys.stderr)
        lines.append(report("sem RAG", compute_metrics(*run(analyzer, essays, use_rag=False, delay=delay))))
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main())
