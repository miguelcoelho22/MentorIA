import pytest

from ai_service.ingest import ingest
from conftest import CORPUS_DIR


class MemoryStore:
    def __init__(self):
        self.sources = {}
        self.chunks = {}

    def ensure_schema(self):
        pass

    def source_hashes(self):
        return dict(self.sources)

    def begin_source(self, source):
        self.sources[source] = "pending"
        self.chunks.setdefault(source, {})

    def chunk_hashes(self, source):
        return set(self.chunks.get(source, {}))

    def delete_chunks(self, source, hashes):
        for h in hashes:
            self.chunks[source].pop(h, None)

    def insert_chunks(self, source, items):
        for h, chunk, embedding in items:
            self.chunks[source][h] = (chunk, embedding)

    def finish_source(self, source, content_hash):
        self.sources[source] = content_hash

    def remove_sources(self, sources):
        for name in sources:
            self.sources.pop(name, None)
            self.chunks.pop(name, None)


class CountingEmbedder:
    def __init__(self, fail_after: int | None = None):
        self.texts = 0
        self.calls = 0
        self.fail_after = fail_after

    def __call__(self, texts):
        if self.fail_after is not None and self.calls >= self.fail_after:
            raise RuntimeError("limite diário")
        self.calls += 1
        self.texts += len(texts)
        return [[0.0, 1.0, 0.0] for _ in texts]


def copy_examples(target):
    for path in (CORPUS_DIR / "exemplos").glob("*.md"):
        (target / path.name).write_text(path.read_text(encoding="utf-8"), encoding="utf-8")


def test_ingest_is_incremental_and_removes_deleted_sources(tmp_path):
    copy_examples(tmp_path)
    store = MemoryStore()
    store.sources["antigo.md"] = "hash-velho"
    embed = CountingEmbedder()

    first = ingest(store, embed, tmp_path, "m1", batch_size=2)
    assert len(first.ingested) == 5 and first.removed == ["antigo.md"]
    assert embed.texts == 15 and embed.calls == 10

    second = ingest(store, embed, tmp_path, "m1", batch_size=2)
    assert second.ingested == [] and len(second.unchanged) == 5
    assert embed.texts == 15

    target = tmp_path / "c5-intervencao.md"
    target.write_text(target.read_text(encoding="utf-8") + "\n---\ncompetency: 5\nlevel: 80\n---\nNovo trecho.\n",
                      encoding="utf-8")
    third = ingest(store, embed, tmp_path, "m1", batch_size=2)
    assert third.ingested == ["c5-intervencao.md"]
    assert embed.texts == 16
    assert len(store.chunks["c5-intervencao.md"]) == 4


def test_ingest_resumes_after_failure_without_reembedding(tmp_path):
    copy_examples(tmp_path)
    store = MemoryStore()

    with pytest.raises(RuntimeError):
        ingest(store, CountingEmbedder(fail_after=4), tmp_path, "m1", batch_size=1)
    assert "pending" in store.sources.values()
    saved = sum(len(chunks) for chunks in store.chunks.values())
    assert saved == 4

    resumed = CountingEmbedder()
    report = ingest(store, resumed, tmp_path, "m1", batch_size=1)
    assert resumed.texts == 15 - saved
    assert "pending" not in store.sources.values()
    assert report.unchanged == ["c1-norma-padrao.md"]
    assert len(report.ingested) == 4


def test_changing_embedding_model_reembeds_everything(tmp_path):
    copy_examples(tmp_path)
    store = MemoryStore()
    ingest(store, CountingEmbedder(), tmp_path, "openai:a:3", batch_size=50)

    switched = CountingEmbedder()
    report = ingest(store, switched, tmp_path, "gemini:b:3", batch_size=50)
    assert len(report.ingested) == 5 and switched.texts == 15
    assert sum(len(chunks) for chunks in store.chunks.values()) == 15
