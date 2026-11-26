import asyncio
import json
from uuid import uuid4

import httpx
import pytest

from ai_service.analysis import AnalysisRequest, Analyzer, InvalidAnalysis
from ai_service.rabbitmq import handle_command
from ai_service.resilience import CircuitBreaker, CircuitOpenError, Metrics, TtlCache
from ai_service.tools import history_tool, summarize_history
from conftest import FakeLlm, FakeRetriever, fake_embed


class Clock:
    def __init__(self):
        self.now = 0.0

    def __call__(self):
        return self.now


def boom():
    raise TimeoutError("provedor fora do ar")


def test_circuit_opens_after_threshold_and_fails_fast():
    clock = Clock()
    breaker = CircuitBreaker(failure_threshold=2, open_seconds=30, clock=clock)
    for _ in range(2):
        with pytest.raises(TimeoutError):
            breaker.call(boom)
    assert breaker.state == "OPEN"
    calls = []
    with pytest.raises(CircuitOpenError):
        breaker.call(lambda: calls.append(1))
    assert calls == []


def test_circuit_half_open_closes_after_successful_trial():
    clock = Clock()
    breaker = CircuitBreaker(failure_threshold=1, open_seconds=10, clock=clock)
    with pytest.raises(TimeoutError):
        breaker.call(boom)
    clock.now = 11
    assert breaker.state == "HALF_OPEN"
    assert breaker.call(lambda: "ok") == "ok"
    assert breaker.state == "CLOSED"


def test_circuit_half_open_reopens_when_trial_fails():
    clock = Clock()
    breaker = CircuitBreaker(failure_threshold=1, open_seconds=10, clock=clock)
    with pytest.raises(TimeoutError):
        breaker.call(boom)
    clock.now = 11
    with pytest.raises(TimeoutError):
        breaker.call(boom)
    assert breaker.state == "OPEN"


def test_ignored_errors_do_not_open_circuit():
    breaker = CircuitBreaker(failure_threshold=1, ignore=(InvalidAnalysis,))

    def invalid():
        raise InvalidAnalysis("fora do contrato")

    with pytest.raises(InvalidAnalysis):
        breaker.call(invalid)
    assert breaker.state == "CLOSED"


def test_cache_expires_and_evicts():
    clock = Clock()
    cache = TtlCache(max_items=1, ttl_seconds=5, clock=clock)
    cache.put("a", 1)
    assert cache.get("a") == 1
    cache.put("b", 2)
    assert cache.get("a") is None
    clock.now = 6
    assert cache.get("b") is None
    assert cache.snapshot()["hits"] == 1


def test_analyzer_uses_cache_and_records_metrics(essay):
    llm = FakeLlm()
    metrics = Metrics(price_input_per_million=1.0, price_output_per_million=2.0)
    analyzer = Analyzer(fake_embed, FakeRetriever(), llm, rubric="R", top_k=1,
                        cache=TtlCache(10, 60), metrics=metrics)
    request = AnalysisRequest(**essay)
    first = analyzer.analyze(request)
    llm.messages = None
    second = analyzer.analyze(request)
    assert first == second and llm.messages is None
    op = metrics.snapshot()["operations"]["analysis"]
    assert op["calls"] == 2 and op["cacheHits"] == 1


def test_analyzer_opens_circuit_on_provider_failures(essay):
    analyzer = Analyzer(fake_embed, FakeRetriever(), FakeLlm(error=TimeoutError()), rubric="R", top_k=1,
                        breaker=CircuitBreaker(failure_threshold=2, open_seconds=60))
    request = AnalysisRequest(**essay)
    for _ in range(2):
        with pytest.raises(TimeoutError):
            analyzer.analyze(request)
    with pytest.raises(CircuitOpenError):
        analyzer.analyze(request)


def test_metrics_estimates_cost_at_scale():
    metrics = Metrics(price_input_per_million=0.15, price_output_per_million=0.60)
    metrics.record("analysis", 1200, ok=True, input_tokens=6000, output_tokens=500, llm_calls=1)
    cost = metrics.snapshot()["operations"]["analysis"]["estimatedCostUsd"]
    assert cost["perOperation"] == pytest.approx(0.0012)
    assert cost["per100000"] == pytest.approx(120.0)


def test_history_tool_reads_feedback_service():
    student = str(uuid4())

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/v1/feedbacks"
        assert request.url.params["studentId"] == student
        return httpx.Response(200, json=[{"totalScore": 640, "publishedAt": "2026-11-20T10:00:00Z",
                                          "competencies": [{"id": i, "score": 120 + (i == 1) * 40}
                                                           for i in range(1, 6)]}])

    tool = history_tool("http://feedback", transport=httpx.MockTransport(handler))
    text = tool.invoke({"student_id": student})
    assert "2026-11-20: total=640" in text and "C1=160" in text


def test_history_tool_never_breaks_the_analysis():
    tool = history_tool("http://feedback", transport=httpx.MockTransport(lambda r: httpx.Response(500)))
    assert tool.invoke({"student_id": str(uuid4())}) == "Histórico indisponível no momento."
    assert "Primeira redação" in summarize_history([])


def test_analysis_prompt_includes_tool_history(essay):
    class History:
        def invoke(self, args):
            return f"HISTORICO-{args['student_id']}"

    llm = FakeLlm()
    Analyzer(fake_embed, FakeRetriever(), llm, rubric="R", top_k=1, history=History()).analyze(AnalysisRequest(**essay))
    assert f"HISTORICO-{essay['studentId']}" in llm.messages[1][1]


def command(essay):
    return json.dumps({"sagaId": 7, "requestId": str(uuid4()), "studentId": essay["studentId"],
                       "essayId": essay["essayId"], "theme": essay["theme"], "text": essay["text"]}).encode()


def run_command(body, analyzer):
    sent = []

    async def publish(payload):
        sent.append(json.loads(payload))

    asyncio.run(handle_command(body, analyzer, publish))
    return sent


def test_saga_command_replies_with_scores(essay):
    sent = run_command(command(essay), Analyzer(fake_embed, FakeRetriever(), FakeLlm(), rubric="R", top_k=1))
    assert sent[0]["status"] == "OK" and sent[0]["sagaId"] == 7
    assert [c["id"] for c in sent[0]["competencies"]] == [1, 2, 3, 4, 5]


def test_saga_command_replies_failure_so_saga_can_compensate(essay):
    analyzer = Analyzer(fake_embed, FakeRetriever(), FakeLlm(error=TimeoutError("lento")), rubric="R", top_k=1)
    sent = run_command(command(essay), analyzer)
    assert sent[0]["status"] == "FAILED" and "TimeoutError" in sent[0]["error"]
    assert sent[0]["competencies"] == []


def test_saga_command_without_api_key_replies_failure(essay):
    sent = run_command(command(essay), None)
    assert sent[0]["status"] == "FAILED"


def test_invalid_saga_command_is_dropped():
    assert run_command(b'{"sagaId": 1}', None) == []
