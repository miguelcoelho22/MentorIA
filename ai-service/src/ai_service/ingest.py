import hashlib
import json
import logging
import sys
from dataclasses import dataclass
from pathlib import Path

from ai_service.catalog import Activity, load_catalog
from ai_service.config import Settings
from ai_service.corpus import Chunk, load_sources
from ai_service.providers import EmbedDocuments, document_embedder, embedding_fingerprint
from ai_service.store import VectorStore

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class IngestReport:
    ingested: list[str]
    unchanged: list[str]
    removed: list[str]


def chunk_hash(chunk: Chunk, fingerprint: str) -> str:
    payload = [fingerprint, chunk.competency, chunk.level, chunk.theme, chunk.content,
               list(chunk.scores) if chunk.scores else None]
    return hashlib.sha256(json.dumps(payload, ensure_ascii=False).encode()).hexdigest()


def source_hash(content_hash: str, fingerprint: str) -> str:
    return hashlib.sha256(f"{fingerprint}:{content_hash}".encode()).hexdigest()


def ingest(store: VectorStore, embed_documents: EmbedDocuments, corpus_dir: Path,
           fingerprint: str, batch_size: int) -> IngestReport:
    store.ensure_schema()
    stored = store.source_hashes()
    sources = load_sources(corpus_dir)

    ingested, unchanged = [], []
    for source in sources:
        expected = source_hash(source.content_hash, fingerprint)
        if stored.get(source.name) == expected:
            unchanged.append(source.name)
            continue

        store.begin_source(source.name)
        wanted = {chunk_hash(chunk, fingerprint): chunk for chunk in source.chunks}
        existing = store.chunk_hashes(source.name)
        store.delete_chunks(source.name, sorted(existing - set(wanted)))
        missing = [(h, c) for h, c in wanted.items() if h not in existing]

        for start in range(0, len(missing), batch_size):
            batch = missing[start:start + batch_size]
            embeddings = embed_documents([chunk.content for _, chunk in batch])
            store.insert_chunks(source.name, [(h, c, e) for (h, c), e in zip(batch, embeddings, strict=True)])
            log.info("%s: %d/%d trechos vetorizados", source.name,
                     len(existing & set(wanted)) + start + len(batch), len(wanted))

        store.finish_source(source.name, expected)
        ingested.append(source.name)
        log.info("Ingerido %s (%d trechos, %d novos)", source.name, len(wanted), len(missing))

    removed = sorted(set(stored) - {source.name for source in sources})
    store.remove_sources(removed)
    return IngestReport(ingested, unchanged, removed)


@dataclass(frozen=True)
class CatalogReport:
    embedded: int
    unchanged: int
    removed: int


def activity_hash(activity: Activity, fingerprint: str) -> str:
    payload = [fingerprint, activity.area, activity.subject, activity.competency, activity.search_text]
    return hashlib.sha256(json.dumps(payload, ensure_ascii=False).encode()).hexdigest()


def sync_catalog(store: VectorStore, embed_documents: EmbedDocuments, catalog: dict[str, Activity],
                 fingerprint: str, batch_size: int) -> CatalogReport:
    store.ensure_schema()
    stored = store.activity_hashes()
    wanted = {activity.id: activity_hash(activity, fingerprint) for activity in catalog.values()}
    removed = sorted(set(stored) - set(wanted))
    store.delete_activities(removed)
    missing = [catalog[i] for i, h in wanted.items() if stored.get(i) != h]
    for start in range(0, len(missing), batch_size):
        batch = missing[start:start + batch_size]
        embeddings = embed_documents([activity.search_text for activity in batch])
        store.upsert_activities([(a.id, wanted[a.id], a.area, a.subject, a.competency, e)
                                 for a, e in zip(batch, embeddings, strict=True)])
    return CatalogReport(len(missing), len(wanted) - len(missing), len(removed))


def run_catalog(settings: Settings, store: VectorStore) -> CatalogReport:
    return sync_catalog(store, document_embedder(settings), load_catalog(settings.catalog_dir),
                        embedding_fingerprint(settings), settings.embedding_batch)


def run(settings: Settings, store: VectorStore) -> IngestReport:
    return ingest(store, document_embedder(settings), settings.corpus_dir,
                  embedding_fingerprint(settings), settings.embedding_batch)


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    settings = Settings.from_env()
    if not settings.api_key:
        print("Defina a chave do provedor (OPENAI_API_KEY ou GEMINI_API_KEY) para gerar os embeddings.",
              file=sys.stderr)
        return 1
    store = VectorStore(settings.database_url, settings.embedding_dimensions)
    catalog = run_catalog(settings, store)
    report = run(settings, store)
    print(f"Catálogo de atividades: {catalog.embedded} vetorizadas | {catalog.unchanged} sem mudança"
          f" | {catalog.removed} removidas")
    print(f"Ingeridos: {len(report.ingested)} | sem mudança: {len(report.unchanged)} | removidos: {len(report.removed)}")
    for name in report.ingested:
        print(f"  + {name}")
    for name in report.removed:
        print(f"  - {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
