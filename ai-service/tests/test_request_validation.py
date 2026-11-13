import pytest
from fastapi import HTTPException

from app.main import select_tool_route, vision_structure, vision_track

CLOCK = {
    "run_id": "run-w02-mock-incident-20261110",
    "clock_mode": "EVAL",
    "request_timestamp": "2026-01-01T00:00:03.000Z",
    "tick": 3,
}


def test_select_tool_rejects_extra_fields():
    with pytest.raises(HTTPException) as error:
        select_tool_route(
            {
                **CLOCK,
                "task": "调查北门刚刚发生了什么",
                "tools": [],
                "tool_results": [],
                "model_seed": 20261110,
                "unexpected": True,
            }
        )
    assert error.value.status_code == 400
    assert "unexpected fields" in error.value.detail


def test_select_tool_rejects_wrong_task_type():
    with pytest.raises(HTTPException) as error:
        select_tool_route(
            {
                **CLOCK,
                "task": 42,
                "tools": [],
                "tool_results": [],
            }
        )
    assert error.value.status_code == 400
    assert "task must be a string" in error.value.detail


def test_select_tool_rejects_wrong_clock_type():
    with pytest.raises(HTTPException) as error:
        select_tool_route(
            {
                **CLOCK,
                "tick": "3",
                "task": "调查北门刚刚发生了什么",
                "tools": [],
                "tool_results": [],
            }
        )
    assert error.value.status_code == 400
    assert "tick must be a non-negative integer" in error.value.detail


def test_vision_track_rejects_oversized_width():
    with pytest.raises(HTTPException) as error:
        vision_track(
            {
                **CLOCK,
                "frames": 2,
                "frame_width": 100000,
                "frame_height": 1080,
            }
        )
    assert error.value.status_code == 400
    assert "frame_width is above maximum" in error.value.detail


def test_vision_track_rejects_total_pixel_budget():
    with pytest.raises(HTTPException) as error:
        vision_track(
            {
                **CLOCK,
                "frames": 30,
                "frame_width": 4096,
                "frame_height": 4096,
            }
        )
    assert error.value.status_code == 400
    assert "pixel budget" in error.value.detail


def test_vision_structure_requires_point_fields():
    with pytest.raises(HTTPException) as error:
        vision_structure(
            {
                **CLOCK,
                "case_key": "live",
                "points": [{"frame_index": 0}],
                "faults": [],
            }
        )
    assert error.value.status_code == 400
    assert "missing tick" in error.value.detail
