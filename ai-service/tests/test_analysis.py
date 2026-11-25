import pytest

from ai_service.analysis import (AnalysisRequest, AnalysisResult, Analyzer, CompetencyScore,
                                 InvalidAnalysis, normalize, round_score)
from ai_service.store import EssayReference, Reference
from conftest import FakeLlm, FakeRetriever, fake_embed


@pytest.mark.parametrize("raw,expected", [(-10, 0), (0, 0), (19, 0), (20, 40), (150, 160), (199, 200), (900, 200)])
def test_round_score_snaps_to_enem_levels(raw, expected):
    assert round_score(raw) == expected


def test_normalize_orders_and_deduplicates():
    result = AnalysisResult(competencies=[
        CompetencyScore(id=c, score=100, comment=f" C{c} ") for c in (5, 3, 1, 4, 2, 2)
    ])
    normalized = normalize(result)
    assert [c.id for c in normalized.competencies] == [1, 2, 3, 4, 5]
    assert all(c.score == 120 and c.comment == c.comment.strip() for c in normalized.competencies)


def test_normalize_rejects_missing_competency():
    result = AnalysisResult(competencies=[CompetencyScore(id=c, score=80, comment="x") for c in (1, 2, 3, 4)])
    with pytest.raises(InvalidAnalysis):
        normalize(result)


def test_analyzer_puts_rubric_essay_and_references_in_prompt(essay):
    retriever = FakeRetriever({5: [Reference(5, 200, "Proposta completa com agente e meio.")]})
    llm = FakeLlm()
    analyzer = Analyzer(fake_embed, retriever, llm, rubric="RUBRICA-TESTE", top_k=2)

    result = analyzer.analyze(AnalysisRequest(**essay))

    assert [c.score for c in result.competencies] == [160] * 5
    assert retriever.calls == [(c, 2) for c in range(1, 6)]
    system, human = llm.messages[0][1], llm.messages[1][1]
    assert "RUBRICA-TESTE" in system
    assert essay["text"] in human and essay["theme"] in human
    assert "[nível 200] Proposta completa com agente e meio." in human


def test_analyzer_falls_back_to_rubric_when_vector_search_fails(essay):
    llm = FakeLlm()
    analyzer = Analyzer(fake_embed, FakeRetriever(error=RuntimeError("db down")), llm, rubric="R", top_k=3)

    analyzer.analyze(AnalysisRequest(**essay))

    assert "(sem referências)" in llm.messages[1][1]


def test_analyzer_includes_similar_graded_essays(essay):
    graded = EssayReference("Mobilidade", (160, 120, 120, 160, 80), "Texto de uma redação corrigida.")
    retriever = FakeRetriever(essays=[graded])
    llm = FakeLlm()
    Analyzer(fake_embed, retriever, llm, rubric="R", top_k=1, essay_k=3).analyze(AnalysisRequest(**essay))

    human = llm.messages[1][1]
    assert retriever.essay_calls == [3]
    assert "Notas: C1=160 | C2=120 | C3=120 | C4=160 | C5=80 | total=640" in human
    assert "Texto de uma redação corrigida." in human


def test_analyzer_without_rag_skips_retrieval(essay):
    retriever = FakeRetriever(essays=[EssayReference(None, (0, 0, 0, 0, 0), "x")])
    llm = FakeLlm()
    Analyzer(fake_embed, retriever, llm, rubric="R", top_k=1).analyze(AnalysisRequest(**essay), use_rag=False)

    assert retriever.calls == [] and retriever.essay_calls == []
    assert "(sem redações de referência)" in llm.messages[1][1]
