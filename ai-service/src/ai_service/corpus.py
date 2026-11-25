import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path

RUBRIC_FILE = "rubrica.md"
MAX_CHUNK_CHARS = 1500

_ENTRY = re.compile(r"^---\n(?P<meta>.*?)\n---\n(?P<body>.*?)(?=^---\n|\Z)", re.MULTILINE | re.DOTALL)


@dataclass(frozen=True)
class Chunk:
    competency: int | None
    level: int | None
    theme: str | None
    content: str
    scores: tuple[int, ...] | None = None


@dataclass(frozen=True)
class Source:
    name: str
    content_hash: str
    chunks: list[Chunk]


def load_rubric(corpus_dir: Path) -> str:
    path = corpus_dir / RUBRIC_FILE
    return path.read_text(encoding="utf-8").strip() if path.exists() else ""


def load_sources(corpus_dir: Path) -> list[Source]:
    sources = []
    for path in sorted(p for p in corpus_dir.rglob("*") if p.suffix in {".md", ".jsonl"}):
        if path.name == RUBRIC_FILE or path.name.lower() == "readme.md":
            continue
        text = path.read_text(encoding="utf-8")
        name = path.relative_to(corpus_dir).as_posix()
        chunks = parse_essays(text) if path.suffix == ".jsonl" else parse_entries(text)
        sources.append(Source(name, hashlib.sha256(text.encode()).hexdigest(), chunks))
    return sources


def parse_essays(text: str) -> list[Chunk]:
    chunks = []
    for line in text.splitlines():
        if not line.strip():
            continue
        essay = json.loads(line)
        scores = tuple(int(s) for s in essay["scores"])
        if len(scores) != 5:
            raise ValueError(f"Redação {essay.get('id')} sem as cinco notas.")
        chunks.append(Chunk(None, None, essay.get("theme") or None, essay["text"].strip(), scores))
    return chunks


def parse_entries(text: str) -> list[Chunk]:
    chunks = []
    for match in _ENTRY.finditer(text.replace("\r\n", "\n")):
        meta = _parse_meta(match.group("meta"))
        for piece in split_text(match.group("body")):
            chunks.append(Chunk(
                competency=_int_or_none(meta.get("competency")),
                level=_int_or_none(meta.get("level")),
                theme=meta.get("theme") or None,
                content=piece,
            ))
    return chunks


def split_text(text: str, max_chars: int = MAX_CHUNK_CHARS) -> list[str]:
    paragraphs = [p.strip() for p in re.split(r"\n\s*\n", text) if p.strip()]
    pieces, current = [], ""
    for paragraph in paragraphs:
        candidate = f"{current}\n\n{paragraph}" if current else paragraph
        if len(candidate) <= max_chars or not current:
            current = candidate
        else:
            pieces.append(current)
            current = paragraph
    if current:
        pieces.append(current)
    return pieces


def _parse_meta(block: str) -> dict[str, str]:
    meta = {}
    for line in block.splitlines():
        key, sep, value = line.partition(":")
        if sep:
            meta[key.strip()] = value.strip()
    return meta


def _int_or_none(value: str | None) -> int | None:
    return int(value) if value not in (None, "") else None
