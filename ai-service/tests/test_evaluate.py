import pytest

from ai_service.evaluate import DEFAULT_EVAL_FILE, compute_metrics, load_eval_set


def test_compute_metrics():
    expected = [[120, 120, 120, 120, 120], [200, 160, 160, 160, 120]]
    predicted = [[120, 160, 120, 120, 40], [200, 160, 120, 160, 120]]
    metrics = compute_metrics(expected, predicted)
    assert metrics.count == 2
    assert metrics.mae_by_competency == (0.0, 20.0, 20.0, 0.0, 40.0)
    assert metrics.mae_total == pytest.approx((40 + 40) / 2)
    assert metrics.exact == pytest.approx(7 / 10)
    assert metrics.adjacent == pytest.approx(9 / 10)


def test_eval_set_is_bundled_and_disjoint_from_rag_corpus():
    import json
    from conftest import CORPUS_DIR

    eval_ids = {e["id"] for e in load_eval_set(DEFAULT_EVAL_FILE, 10_000)}
    with (CORPUS_DIR / "essay-br.jsonl").open(encoding="utf-8") as handle:
        corpus_ids = {json.loads(line)["id"] for line in handle}
    assert len(eval_ids) == 200
    assert eval_ids.isdisjoint(corpus_ids)
