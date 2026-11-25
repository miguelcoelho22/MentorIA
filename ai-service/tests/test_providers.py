import math

import pytest

from ai_service.config import Settings
from ai_service.providers import fit_dimensions, is_rate_limit, with_rate_limit_retry


class RateLimited(Exception):
    status_code = 429


def test_fit_dimensions_keeps_matching_vector():
    assert fit_dimensions([0.6, 0.8], 2) == [0.6, 0.8]


def test_fit_dimensions_truncates_and_renormalizes():
    fitted = fit_dimensions([3.0, 4.0, 12.0], 2)
    assert fitted == pytest.approx([0.6, 0.8])
    assert math.isclose(sum(v * v for v in fitted), 1.0)


def test_fit_dimensions_rejects_smaller_vector():
    with pytest.raises(ValueError):
        fit_dimensions([1.0], 3)


def test_retry_waits_on_rate_limit_then_succeeds():
    calls, waits = [], []

    def flaky():
        calls.append(1)
        if len(calls) < 3:
            raise RateLimited()
        return "ok"

    assert with_rate_limit_retry(flaky, attempts=5, wait_seconds=7, sleep=waits.append)() == "ok"
    assert waits == [7, 7]


def test_retry_does_not_hide_other_errors():
    def broken():
        raise ValueError("outro erro")

    with pytest.raises(ValueError):
        with_rate_limit_retry(broken, attempts=5, wait_seconds=0, sleep=lambda _: None)()
    assert not is_rate_limit(ValueError())


def test_gemini_preset(monkeypatch):
    for var in ("OPENAI_API_KEY", "LLM_API_KEY", "LLM_MODEL", "EMBEDDING_MODEL", "LLM_BASE_URL"):
        monkeypatch.delenv(var, raising=False)
    monkeypatch.setenv("LLM_PROVIDER", "gemini")
    monkeypatch.setenv("GEMINI_API_KEY", "g-key")
    monkeypatch.setenv("OPENAI_API_KEY", "")
    settings = Settings.from_env()
    assert settings.api_key == "g-key"
    assert settings.base_url.startswith("https://generativelanguage.googleapis.com/")
    assert settings.llm_model == "gemini-3.1-flash-lite"
    assert settings.embedding_model == "gemini-embedding-001"
    assert settings.send_dimensions is False and settings.openai_compatible


def test_openai_is_default_and_ignores_gemini_key(monkeypatch):
    for var in ("LLM_PROVIDER", "OPENAI_API_KEY", "LLM_API_KEY", "LLM_BASE_URL"):
        monkeypatch.delenv(var, raising=False)
    monkeypatch.setenv("GEMINI_API_KEY", "g-key")
    settings = Settings.from_env()
    assert settings.provider == "openai" and settings.api_key is None
    assert settings.base_url is None and settings.send_dimensions is True


def test_invalid_provider(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "outro")
    with pytest.raises(ValueError):
        Settings.from_env()
