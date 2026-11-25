from fastapi.testclient import TestClient

from ai_service.analysis import AnalysisResult, Analyzer, CompetencyScore
from ai_service.main import create_app
from conftest import FakeLlm, FakeRetriever, fake_embed, settings


def client_with(llm: FakeLlm) -> TestClient:
    analyzer = Analyzer(fake_embed, FakeRetriever(), llm, rubric="R", top_k=1)
    return TestClient(create_app(analyzer=analyzer, settings=settings()))


def test_analysis_returns_contract_expected_by_feedback(essay):
    with client_with(FakeLlm()) as client:
        response = client.post("/api/v1/analysis", json=essay)
    assert response.status_code == 200
    competencies = response.json()["competencies"]
    assert [c["id"] for c in competencies] == [1, 2, 3, 4, 5]
    assert all(set(c) == {"id", "score", "comment"} and 0 <= c["score"] <= 200 for c in competencies)


def test_invalid_llm_output_returns_502(essay):
    bad = AnalysisResult(competencies=[CompetencyScore(id=1, score=80, comment="x")])
    with client_with(FakeLlm(result=bad)) as client:
        response = client.post("/api/v1/analysis", json=essay)
    assert response.status_code == 502
    assert response.headers["content-type"].startswith("application/problem+json")


def test_llm_failure_returns_503(essay):
    with client_with(FakeLlm(error=TimeoutError())) as client:
        response = client.post("/api/v1/analysis", json=essay)
    assert response.status_code == 503
    assert "TimeoutError" in response.json()["detail"]


class ProviderError(Exception):
    status_code = 404
    body = [{"error": {"code": 404, "message": "This model is no longer available to new users."}}]


def test_provider_error_message_is_reported(essay):
    with client_with(FakeLlm(error=ProviderError("Error code: 404"))) as client:
        response = client.post("/api/v1/analysis", json=essay)
    assert response.status_code == 503
    assert "no longer available to new users" in response.json()["detail"]


def test_without_api_key_analysis_is_disabled(essay):
    with TestClient(create_app(settings=settings(api_key=None))) as client:
        assert client.get("/health").json()["analysis"] == "disabled"
        response = client.post("/api/v1/analysis", json=essay)
    assert response.status_code == 503
    assert response.json()["type"].endswith("/ai-unavailable")


def test_rejects_incomplete_request(essay):
    del essay["text"]
    with client_with(FakeLlm()) as client:
        assert client.post("/api/v1/analysis", json=essay).status_code == 422
