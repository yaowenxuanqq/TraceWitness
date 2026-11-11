import numpy as np

from app.vision import (
    InferenceError,
    Tracker,
    analyze_prepared,
    analyze_sample,
    downsample_width,
    get_session,
    measure_detect_fps,
    run_luma,
    sample_frame,
    to_nchw,
)


def test_sample_track_is_stable():
    first = analyze_sample(8)
    second = analyze_sample(8)
    assert first == second
    assert first["faults"] == []
    assert len(first["tracks"]) == 1
    points = first["tracks"][0]["points"]
    assert len(points) == 8
    xs = [point["x"] for point in points]
    assert xs == sorted(xs)
    assert xs[0] < xs[-1]
    assert first["tracks"][0]["local_id"] == "track-1"


def test_track_window_drops_stale_match():
    tracker = Tracker(window=5)
    box = {"x": 0.20, "y": 0.30, "w": 0.10, "h": 0.20, "confidence": 0.90}
    tracker.update(0, 0, [dict(box)])
    tracker.update(7, 7, [dict(box)])
    assert [track["local_id"] for track in tracker.tracks] == ["track-1", "track-2"]


def test_bad_tensor_is_recorded_and_skipped():
    bad = np.zeros((2, 2), dtype=np.float32)
    good = to_nchw(downsample_width(sample_frame(0)))
    result = analyze_prepared([bad, good])
    assert result["faults"][0]["fault_type"] == "inference_error"
    assert result["faults"][0]["frame_index"] == 0
    assert len(result["tracks"]) == 1
    assert result["tracks"][0]["points"][0]["frame_index"] == 1


def test_downsampled_1080p_detects_at_least_5fps():
    fps = measure_detect_fps()
    assert fps >= 5, fps


def test_session_rejects_rank_error():
    try:
        run_luma(get_session(), np.zeros((2, 2), dtype=np.float32))
    except InferenceError:
        return
    raise AssertionError("bad tensor should fail")
