from ai_service.corpus import load_rubric, load_sources, parse_entries, parse_essays, split_text
from conftest import CORPUS_DIR


def test_parse_entries_reads_metadata_of_each_block():
    text = """---
competency: 5
level: 160
theme: Tema A
---
Primeiro trecho.

---
competency: 2
level: 40
---
Segundo trecho.
"""
    chunks = parse_entries(text)
    assert [(c.competency, c.level, c.theme) for c in chunks] == [(5, 160, "Tema A"), (2, 40, None)]
    assert chunks[0].content == "Primeiro trecho."
    assert chunks[1].content == "Segundo trecho."


def test_split_text_keeps_paragraphs_under_limit():
    text = "\n\n".join(["a" * 40] * 5)
    pieces = split_text(text, max_chars=100)
    assert len(pieces) == 3
    assert all(len(p) <= 100 for p in pieces)


def test_bundled_excerpts_cover_every_competency():
    excerpts = [c for s in load_sources(CORPUS_DIR) for c in s.chunks if c.scores is None]
    assert {c.competency for c in excerpts} == {1, 2, 3, 4, 5}
    assert all(c.level in {0, 40, 80, 120, 160, 200} for c in excerpts)


def test_bundled_essays_have_valid_enem_scores():
    sources = {s.name: s for s in load_sources(CORPUS_DIR)}
    assert "README.md" not in sources
    essays = sources["essay-br.jsonl"].chunks
    assert essays
    assert all(len(e.scores) == 5 and all(s in {0, 40, 80, 120, 160, 200} for s in e.scores) for e in essays)
    assert all(e.competency is None and e.content for e in essays)


def test_parse_essays_reads_jsonl():
    line = '{"id": "x", "theme": "Tema", "scores": [120, 160, 120, 160, 80], "text": " Texto. "}'
    [essay] = parse_essays(line + "\n\n")
    assert essay.scores == (120, 160, 120, 160, 80)
    assert essay.theme == "Tema" and essay.content == "Texto."


def test_rubric_is_loaded_and_not_ingested():
    assert "Competência 5" in load_rubric(CORPUS_DIR)
    assert "rubrica.md" not in {s.name for s in load_sources(CORPUS_DIR)}
