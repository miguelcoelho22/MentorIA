import pytest

from ai_service.catalog import AREAS, SUBJECTS, TOPICS, load_catalog
from conftest import CATALOG_DIR

CATALOG = load_catalog(CATALOG_DIR)


def test_catalog_covers_every_enem_area_and_subject():
    assert {a.area for a in CATALOG.values()} == set(AREAS)
    assert {a.subject for a in CATALOG.values()} == set(SUBJECTS)


def test_redacao_keeps_every_competency_and_level():
    for competency in range(1, 6):
        levels = {a.level for a in CATALOG.values() if a.competency == competency}
        assert {40, 80, 120, 160} <= levels


def test_topics_belong_to_the_activity_subject():
    assert all(TOPICS[t] == a.subject for a in CATALOG.values() for t in a.topics)
    assert all(a.title and a.description and a.minutes > 0 for a in CATALOG.values())


def test_rejects_duplicated_or_inconsistent_activities(tmp_path):
    entry = ("---\nid: x\nsubject: fisica\ndifficulty: basico\nminutes: 10\ntopics: mecanica\ntitle: T\n---\n"
             "Descrição.\n")
    (tmp_path / "a.md").write_text(entry + entry, encoding="utf-8")
    with pytest.raises(ValueError, match="duplicada"):
        load_catalog(tmp_path)
    (tmp_path / "a.md").write_text(entry.replace("mecanica", "genetica"), encoding="utf-8")
    with pytest.raises(ValueError, match="inválida"):
        load_catalog(tmp_path)
    (tmp_path / "a.md").write_text(entry.replace("subject: fisica", "subject: astrologia"), encoding="utf-8")
    with pytest.raises(ValueError, match="inválida"):
        load_catalog(tmp_path)
