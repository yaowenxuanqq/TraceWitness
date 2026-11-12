from app.attributes import structure, structure_case
from app.vision import analyze_sample


def test_attribute_head_runs_once_on_the_sample_track():
    tracked = analyze_sample(8)
    result = structure(tracked["tracks"][0]["points"], tracked["faults"])
    assert result["attribute_calls"] == 1
    assert result["mode"] == "preferred"
    assert result["frame_kind"] == "crop"
    assert result["chosen_frame"] == 0
    assert result["masked"]["face_region"] == "redacted"
    assert result["masked"]["subject_token"] == "masked"
    assert "embedding" not in result["masked"]
    again = structure(tracked["tracks"][0]["points"], tracked["faults"])
    assert again == result


def test_gate_failure_uses_the_preferred_moment_full_frame():
    result = structure_case("gate_failure")
    assert result["attribute_calls"] == 1
    assert result["mode"] == "gate_failure"
    assert result["frame_kind"] == "full_frame"
    assert result["chosen_frame"] == 0
    assert result["face_quality"] == 0.20


def test_track_break_uses_the_best_valid_frame_not_the_latest():
    result = structure_case("track_break")
    assert result["attribute_calls"] == 1
    assert result["mode"] == "track_break"
    assert result["frame_kind"] == "crop"
    assert result["chosen_frame"] == 0
    assert result["chosen_frame"] != 8


def test_inference_error_skips_the_faulted_frame_and_not_the_latest():
    points = [
        {
            "frame_index": 0,
            "tick": 0,
            "x": 0.2,
            "y": 0.3,
            "w": 0.1,
            "h": 0.4,
            "confidence": 0.90,
            "face_quality": 0.92,
            "body_quality": 0.92,
        },
        {
            "frame_index": 1,
            "tick": 1,
            "x": 0.2,
            "y": 0.3,
            "w": 0.1,
            "h": 0.4,
            "confidence": 0.99,
            "face_quality": 0.99,
            "body_quality": 0.99,
        },
        {
            "frame_index": 2,
            "tick": 2,
            "x": 0.2,
            "y": 0.3,
            "w": 0.1,
            "h": 0.4,
            "confidence": 0.90,
            "face_quality": 0.40,
            "body_quality": 0.40,
        },
    ]
    faults = [{"frame_index": 1, "fault_type": "inference_error", "detail": "bad tensor"}]
    result = structure(points, faults)
    assert result["attribute_calls"] == 1
    assert result["mode"] == "inference_error"
    assert result["chosen_frame"] == 0
