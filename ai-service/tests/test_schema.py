import pytest

from app.adapters import validate_report, validate_tool_calls


def test_extra_report_field_is_rejected():
    report = {
        "conclusion": "证据不足",
        "source_ids": [],
        "confidence": 0,
        "insufficient_evidence": True,
        "requested_actions": [],
        "unexpected": "accepted?",
    }
    with pytest.raises(ValueError, match="report schema"):
        validate_report(report)


def test_valid_report_keeps_fields():
    report = {
        "conclusion": "证据不足",
        "source_ids": [],
        "confidence": 0,
        "insufficient_evidence": True,
        "requested_actions": [],
    }
    assert validate_report(report)["conclusion"] == "证据不足"


def test_extra_tool_field_is_rejected():
    with pytest.raises(ValueError, match="tool arguments"):
        validate_tool_calls(
            {
                "tool_calls": [
                    {
                        "tool_name": "search_events",
                        "arguments": {"limit": 5, "unexpected": "accepted?"},
                    }
                ]
            }
        )
