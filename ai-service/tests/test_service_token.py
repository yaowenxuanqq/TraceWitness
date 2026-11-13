import pytest
from fastapi import HTTPException

from app.adapters import OpenAICompatibleAdapter
from app.main import _assert_service_token, require_service_token


def test_missing_or_wrong_service_token_is_rejected(monkeypatch):
    monkeypatch.setenv("TRACEWITNESS_SERVICE_TOKEN", "expected-token")
    with pytest.raises(HTTPException) as missing:
        require_service_token(None)
    with pytest.raises(HTTPException) as wrong:
        require_service_token("Bearer other-token")
    assert missing.value.status_code == 401
    assert wrong.value.status_code == 401
    assert missing.value.detail == "unauthorized"
    assert "expected-token" not in str(missing.value.detail)


def test_matching_service_token_is_accepted(monkeypatch):
    monkeypatch.setenv("TRACEWITNESS_SERVICE_TOKEN", "expected-token")
    require_service_token("Bearer expected-token")


def test_unset_service_token_is_rejected(monkeypatch):
    monkeypatch.delenv("TRACEWITNESS_SERVICE_TOKEN", raising=False)
    with pytest.raises(HTTPException) as error:
        require_service_token("Bearer anything")
    assert error.value.status_code == 401


def test_non_mock_startup_requires_service_token(monkeypatch):
    monkeypatch.setenv("TRACEWITNESS_MODE", "MODEL_API")
    monkeypatch.delenv("MOCK_MODE", raising=False)
    monkeypatch.delenv("TRACEWITNESS_SERVICE_TOKEN", raising=False)
    with pytest.raises(RuntimeError, match="TRACEWITNESS_SERVICE_TOKEN"):
        _assert_service_token()
    monkeypatch.setenv("TRACEWITNESS_SERVICE_TOKEN", "present")
    _assert_service_token()


def test_empty_model_key_stays_closed_unless_switch_is_explicit():
    closed = OpenAICompatibleAdapter("http://127.0.0.1:9", "", "local-model")
    with pytest.raises(Exception, match="未配置"):
        closed._configured()
    opened = OpenAICompatibleAdapter("http://127.0.0.1:9", "", "local-model", allow_empty_key=True)
    opened._configured()
