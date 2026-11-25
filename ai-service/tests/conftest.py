from pathlib import Path
from uuid import uuid4

import pytest

from ai_service.analysis import AnalysisResult, CompetencyScore
from ai_service.config import Settings
from ai_service.store import EssayReference, Reference

CORPUS_DIR = Path(__file__).resolve().parents[1] / "corpus"
CATALOG_DIR = Path(__file__).resolve().parents[1] / "catalog"


class FakeLlm:
    def __init__(self, result: AnalysisResult | None = None, error: Exception | None = None):
        self.result = result or AnalysisResult(competencies=[
            CompetencyScore(id=c, score=150, comment=f"Comentário C{c}") for c in range(1, 6)
        ])
        self.error = error
        self.messages = None

    def invoke(self, messages):
        self.messages = messages
        if self.error:
            raise self.error
        return self.result


class FakeRetriever:
    def __init__(self, references: dict[int, list[Reference]] | None = None, error: Exception | None = None,
                 essays: list[EssayReference] | None = None):
        self.references = references or {}
        self.essays = essays or []
        self.error = error
        self.calls = []
        self.essay_calls = []

    def search_essays(self, embedding, limit):
        self.essay_calls.append(limit)
        if self.error:
            raise self.error
        return self.essays[:limit]

    def search(self, embedding, competency, limit):
        self.calls.append((competency, limit))
        if self.error:
            raise self.error
        return self.references.get(competency, [])[:limit]


def fake_embed(text: str) -> list[float]:
    return [float(len(text) % 7), 1.0, 0.5]


@pytest.fixture
def essay():
    return {
        "studentId": str(uuid4()),
        "essayId": str(uuid4()),
        "theme": "Desafios da mobilidade urbana no Brasil",
        "text": "Nas grandes cidades, o transporte coletivo é insuficiente.",
    }


def settings(**overrides) -> Settings:
    values = dict(
        database_url="postgresql://unused/ai_db", provider="openai", api_key=None, base_url=None,
        llm_model="fake", llm_timeout_seconds=1, llm_max_retries=0, structured_output="json_schema",
        embedding_model="fake", embedding_dimensions=3, embedding_batch=10, send_dimensions=True,
        rate_limit_wait_seconds=0, top_k=2, essay_k=2, corpus_dir=CORPUS_DIR, catalog_dir=CATALOG_DIR,
        auto_ingest=False,
    )
    values.update(overrides)
    return Settings(**values)
