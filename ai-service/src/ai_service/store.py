from dataclasses import dataclass
from typing import Protocol, Sequence

import numpy as np
import psycopg
from pgvector.psycopg import register_vector

from ai_service.corpus import Chunk


@dataclass(frozen=True)
class Reference:
    competency: int
    level: int | None
    content: str


@dataclass(frozen=True)
class EssayReference:
    theme: str | None
    scores: tuple[int, ...]
    content: str


class Retriever(Protocol):
    def search(self, embedding: Sequence[float], competency: int, limit: int) -> list[Reference]: ...

    def search_essays(self, embedding: Sequence[float], limit: int) -> list[EssayReference]: ...


class VectorStore:
    def __init__(self, database_url: str, dimensions: int):
        self._url = database_url
        self._dimensions = dimensions

    def _connect(self) -> psycopg.Connection:
        conn = psycopg.connect(self._url, autocommit=True)
        register_vector(conn)
        return conn

    def ensure_schema(self) -> None:
        with psycopg.connect(self._url, autocommit=True) as conn:
            conn.execute("CREATE EXTENSION IF NOT EXISTS vector")
            conn.execute("""
                CREATE TABLE IF NOT EXISTS corpus_sources (
                    source TEXT PRIMARY KEY,
                    content_hash TEXT NOT NULL,
                    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now()
                )
            """)
            conn.execute(f"""
                CREATE TABLE IF NOT EXISTS corpus_chunks (
                    id BIGSERIAL PRIMARY KEY,
                    source TEXT NOT NULL REFERENCES corpus_sources (source) ON DELETE CASCADE,
                    competency SMALLINT,
                    level SMALLINT,
                    theme TEXT,
                    content TEXT NOT NULL,
                    scores SMALLINT[],
                    embedding vector({self._dimensions}) NOT NULL
                )
            """)
            conn.execute("ALTER TABLE corpus_chunks ADD COLUMN IF NOT EXISTS scores SMALLINT[]")
            conn.execute("ALTER TABLE corpus_chunks ADD COLUMN IF NOT EXISTS chunk_hash TEXT")
            conn.execute("DELETE FROM corpus_chunks WHERE chunk_hash IS NULL")
            conn.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS corpus_chunks_source_hash_idx
                ON corpus_chunks (source, chunk_hash)
            """)
            conn.execute("""
                CREATE INDEX IF NOT EXISTS corpus_chunks_embedding_idx
                ON corpus_chunks USING hnsw (embedding vector_cosine_ops)
            """)
            conn.execute("CREATE INDEX IF NOT EXISTS corpus_chunks_competency_idx ON corpus_chunks (competency)")

    def source_hashes(self) -> dict[str, str]:
        with self._connect() as conn:
            return dict(conn.execute("SELECT source, content_hash FROM corpus_sources").fetchall())

    def begin_source(self, source: str) -> None:
        with self._connect() as conn:
            conn.execute("""
                INSERT INTO corpus_sources (source, content_hash) VALUES (%s, 'pending')
                ON CONFLICT (source) DO UPDATE SET content_hash = 'pending'
            """, (source,))

    def chunk_hashes(self, source: str) -> set[str]:
        with self._connect() as conn:
            rows = conn.execute("SELECT chunk_hash FROM corpus_chunks WHERE source = %s", (source,)).fetchall()
        return {row[0] for row in rows}

    def delete_chunks(self, source: str, chunk_hashes: Sequence[str]) -> None:
        if chunk_hashes:
            with self._connect() as conn:
                conn.execute("DELETE FROM corpus_chunks WHERE source = %s AND chunk_hash = ANY(%s)",
                             (source, list(chunk_hashes)))

    def insert_chunks(self, source: str, items: Sequence[tuple[str, Chunk, Sequence[float]]]) -> None:
        with self._connect() as conn, conn.transaction(), conn.cursor() as cur:
            cur.executemany(
                """
                INSERT INTO corpus_chunks (source, chunk_hash, competency, level, theme, content, scores, embedding)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (source, chunk_hash) DO NOTHING
                """,
                [(source, chunk_hash, c.competency, c.level, c.theme, c.content,
                  list(c.scores) if c.scores else None, np.asarray(e, dtype=np.float32))
                 for chunk_hash, c, e in items],
            )

    def finish_source(self, source: str, content_hash: str) -> None:
        with self._connect() as conn:
            conn.execute("UPDATE corpus_sources SET content_hash = %s, ingested_at = now() WHERE source = %s",
                         (content_hash, source))

    def remove_sources(self, sources: Sequence[str]) -> None:
        if sources:
            with self._connect() as conn:
                conn.execute("DELETE FROM corpus_sources WHERE source = ANY(%s)", (list(sources),))

    def search(self, embedding: Sequence[float], competency: int, limit: int) -> list[Reference]:
        with self._connect() as conn:
            rows = conn.execute(
                """
                SELECT competency, level, content FROM corpus_chunks
                WHERE competency = %s
                ORDER BY embedding <=> %s
                LIMIT %s
                """,
                (competency, np.asarray(embedding, dtype=np.float32), limit),
            ).fetchall()
        return [Reference(row[0], row[1], row[2]) for row in rows]

    def search_essays(self, embedding: Sequence[float], limit: int) -> list[EssayReference]:
        with self._connect() as conn:
            rows = conn.execute(
                """
                SELECT theme, scores, content FROM corpus_chunks
                WHERE scores IS NOT NULL
                ORDER BY embedding <=> %s
                LIMIT %s
                """,
                (np.asarray(embedding, dtype=np.float32), limit),
            ).fetchall()
        return [EssayReference(row[0], tuple(row[1]), row[2]) for row in rows]
