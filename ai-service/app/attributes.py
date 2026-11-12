"""Preferred-frame attributes, two frame fallbacks, and de-identification.

Business timestamps stay with the caller. structure() invokes the attribute head once.
"""

from decimal import ROUND_HALF_UP, Decimal

FACE_MIN = Decimal("0.50")
BODY_MIN = Decimal("0.50")
CONF_MIN = Decimal("0.60")
TRACK_WINDOW = 5


def quantize(value):
    return Decimal(str(value)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def qualities(point):
    if "face_quality" in point or "body_quality" in point:
        return quantize(point["face_quality"]), quantize(point["body_quality"])
    body = min(Decimal("1.00"), quantize(Decimal(str(point["h"])) / Decimal("0.25")))
    face = min(Decimal("1.00"), quantize(point["confidence"]))
    return face, body


def _rank(point):
    face, body = qualities(point)
    return (body, face, -int(point["frame_index"]))


def passes_gate(point):
    face, body = qualities(point)
    return body >= BODY_MIN and face >= FACE_MIN and quantize(point["confidence"]) >= CONF_MIN


def track_broken(points):
    ordered = sorted(points, key=lambda point: int(point["frame_index"]))
    for previous, current in zip(ordered, ordered[1:], strict=False):
        if int(current["frame_index"]) - int(previous["frame_index"]) > TRACK_WINDOW:
            return True
    return False


def attribute_head(point):
    """Clothing attributes for one frame. This is not a face embedding."""
    face, body = qualities(point)
    upper = "light" if quantize(point["confidence"]) >= Decimal("0.75") else "dark"
    return {
        "upper_color": upper,
        "posture": "upright" if body >= Decimal("0.50") else "partial",
        "face_quality": float(face),
        "body_quality": float(body),
    }


def deidentify(attributes, point):
    face_h = min(quantize(Decimal(str(point["h"])) * Decimal("0.20")), Decimal("0.08"))
    face_w = min(quantize(point["w"]), Decimal("0.08"))
    return {
        "upper_color": attributes["upper_color"],
        "posture": attributes["posture"],
        "subject_token": "masked",
        "face_region": "redacted",
        "mask_box": {
            "x": float(quantize(point["x"])),
            "y": float(quantize(point["y"])),
            "w": float(face_w),
            "h": float(face_h),
        },
    }


def _point(frame_index, tick, face, body, confidence=0.90):
    return {
        "frame_index": frame_index,
        "tick": tick,
        "x": 0.20,
        "y": 0.30,
        "w": 0.10,
        "h": 0.40,
        "confidence": confidence,
        "face_quality": face,
        "body_quality": body,
    }


def gate_failure_case():
    return (
        [
            _point(0, 0, 0.20, 0.80),
            _point(1, 1, 0.10, 0.30),
        ],
        [],
    )


def track_break_case():
    return (
        [
            _point(0, 0, 0.90, 0.90),
            _point(1, 1, 0.40, 0.40),
            _point(8, 8, 0.70, 0.70),
        ],
        [],
    )


def structure(points, faults=None):
    if not points:
        raise ValueError("no points")
    faults = list(faults or [])
    fault_frames = set()
    for fault in faults:
        if fault.get("frame_index") is not None:
            fault_frames.add(int(fault["frame_index"]))
    valid = [point for point in points if int(point["frame_index"]) not in fault_frames]
    if not valid:
        raise ValueError("no valid frames")

    calls = {"n": 0}

    def once(point):
        calls["n"] += 1
        if calls["n"] != 1:
            raise RuntimeError("attribute head called more than once")
        return attribute_head(point)

    has_inference = any(fault.get("fault_type") == "inference_error" for fault in faults)
    if has_inference or track_broken(points):
        chosen = max(valid, key=_rank)
        mode = "inference_error" if has_inference else "track_break"
        frame_kind = "crop"
        attributes = once(chosen)
    else:
        chosen = max(points, key=_rank)
        attributes = once(chosen)
        if passes_gate(chosen):
            mode = "preferred"
            frame_kind = "crop"
        else:
            mode = "gate_failure"
            frame_kind = "full_frame"

    face, body = qualities(chosen)
    return {
        "mode": mode,
        "frame_kind": frame_kind,
        "chosen_frame": int(chosen["frame_index"]),
        "chosen_tick": int(chosen["tick"]),
        "attribute_calls": calls["n"],
        "face_quality": float(face),
        "body_quality": float(body),
        "attributes": attributes,
        "masked": deidentify(attributes, chosen),
    }


def structure_case(case_key, points=None, faults=None):
    if case_key == "gate_failure":
        points, faults = gate_failure_case()
    elif case_key == "track_break":
        points, faults = track_break_case()
    result = structure(points or [], faults or [])
    result["case_key"] = case_key
    return result
