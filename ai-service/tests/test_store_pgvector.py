import os

import pytest

from ai_service.corpus import Chunk
from ai_service.store import VectorStore

DATABASE_URL = os.getenv("TEST_DATABASE_URL")
pytestmark = pytest.mark.skipif(not DATABASE_URL, reason="defina TEST_DATABASE_URL para testar com o pgvector")


def test_search_returns_nearest_chunks_of_the_competency():
    store = VectorStore(DATABASE_URL, dimensions=3)
    store.ensure_schema()
    chunks = [
        Chunk(5, 200, None, "proposta completa"),
        Chunk(5, 40, None, "proposta vaga"),
        Chunk(1, 120, None, "desvios gramaticais"),
        Chunk(None, None, "Tema", "redação inteira", (120, 160, 120, 160, 80)),
    ]
    store.begin_source("teste.md")
    store.insert_chunks("teste.md", [(f"h{i}", c, e) for i, (c, e) in
                                     enumerate(zip(chunks, [[1, 0, 0], [0, 1, 0], [1, 0, 0], [0, 0, 1]]))])
    store.insert_chunks("teste.md", [("h0", chunks[0], [1, 0, 0])])
    store.finish_source("teste.md", "h1")
    try:
        results = store.search([0.9, 0.1, 0], competency=5, limit=2)
        assert [r.content for r in results] == ["proposta completa", "proposta vaga"]
        assert store.source_hashes()["teste.md"] == "h1"
        assert store.chunk_hashes("teste.md") == {"h0", "h1", "h2", "h3"}
        store.delete_chunks("teste.md", ["h2"])
        assert "h2" not in store.chunk_hashes("teste.md")
        [essay] = store.search_essays([0, 0, 1], limit=5)
        assert essay.scores == (120, 160, 120, 160, 80) and essay.theme == "Tema"
    finally:
        store.remove_sources(["teste.md"])
    assert "teste.md" not in store.source_hashes()


def test_activity_search_filters_by_area_subject_and_competency():
    store = VectorStore(DATABASE_URL, dimensions=3)
    store.ensure_schema()
    store.upsert_activities([
        ("fis-perto", "h", "natureza", "fisica", None, [1, 0, 0]),
        ("fis-longe", "h", "natureza", "fisica", None, [0, 1, 0]),
        ("qui-perto", "h", "natureza", "quimica", None, [1, 0, 0]),
        ("red-c5", "h", "redacao", "redacao", 5, [1, 0, 0]),
        ("red-c1", "h", "redacao", "redacao", 1, [1, 0, 0]),
    ])
    ids = ["fis-perto", "fis-longe", "qui-perto", "red-c5", "red-c1"]
    try:
        assert store.search_activities([0.9, 0.1, 0], "natureza", "fisica", None, 5) == ["fis-perto", "fis-longe"]
        assert set(store.search_activities([1, 0, 0], "natureza", None, None, 5)) == {"fis-perto", "fis-longe",
                                                                                       "qui-perto"}
        assert store.search_activities([1, 0, 0], "redacao", "redacao", 5, 5) == ["red-c5"]
        assert store.activity_hashes()["fis-perto"] == "h"
    finally:
        store.delete_activities(ids)
    assert not set(ids) & set(store.activity_hashes())
