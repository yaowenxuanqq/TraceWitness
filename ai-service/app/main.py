import os

from fastapi import FastAPI, HTTPException

from app.adapters import (
    MockAdapter,
    ModelCallFailed,
    ModelNotConfigured,
    OpenAICompatibleAdapter,
    draft_report,
    require_clock,
    select_tool,
)
from app.vision import SAMPLE_FRAMES, analyze_sample
from app.attributes import structure_case

app = FastAPI(title="TraceWitness AI")

SELECT_TOOL_FIELDS = frozenset({
    "run_id", "clock_mode", "request_timestamp", "tick",
    "task", "tools", "tool_results", "model_seed",
})
REPORT_FIELDS = frozenset({
    "run_id", "clock_mode", "request_timestamp", "tick",
    "task", "tool_results", "model_seed", "citation_hint",
})
TRACK_FIELDS = frozenset({
    "run_id", "clock_mode", "request_timestamp", "tick",
    "frames", "frame_width", "frame_height",
})
STRUCTURE_FIELDS = frozenset({
    "run_id", "clock_mode", "request_timestamp", "tick",
    "case_key", "points", "faults",
})
POINT_FIELDS = ("frame_index", "tick", "x", "y", "w", "h", "confidence")


def _check_fields(body, allowed):
    if not isinstance(body, dict):
        raise ValueError("body must be an object")
    extra = sorted(set(body) - set(allowed))
    if extra:
        raise ValueError("unexpected fields: " + ", ".join(extra))


def _text(body, name, default=""):
    value = body.get(name, default)
    if not isinstance(value, str):
        raise ValueError(name + " must be a string")
    return value


def _objects(body, name):
    value = body.get(name)
    if value is None:
        return []
    if not isinstance(value, list) or any(not isinstance(item, dict) for item in value):
        raise ValueError(name + " must be a list of objects")
    return value


def _strings(body, name):
    value = body.get(name)
    if value is None:
        return []
    if not isinstance(value, list) or any(not isinstance(item, str) for item in value):
        raise ValueError(name + " must be a list of strings")
    return value


def _integer(body, name, default, minimum=None, maximum=None):
    value = body.get(name, default)
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError(name + " must be an integer")
    if minimum is not None and value < minimum:
        raise ValueError(name + " is below minimum")
    if maximum is not None and value > maximum:
        raise ValueError(name + " is above maximum")
    return value


def _points(body):
    points = _objects(body, "points")
    for index, point in enumerate(points):
        for field in POINT_FIELDS:
            if field not in point:
                raise ValueError("points[%d] missing %s" % (index, field))
        for field in ("frame_index", "tick"):
            if isinstance(point[field], bool) or not isinstance(point[field], int):
                raise ValueError("points[%d].%s must be an integer" % (index, field))
        for field in ("x", "y", "w", "h", "confidence"):
            if isinstance(point[field], bool) or not isinstance(point[field], (int, float)):
                raise ValueError("points[%d].%s must be a number" % (index, field))
    if len(points) > 10000:
        raise ValueError("points is too large")
    return points


def build_adapter():
    mode = os.environ.get("TRACEWITNESS_MODE", "MOCK")
    if mode.upper() == "MOCK" or os.environ.get("MOCK_MODE") == "1":
        return MockAdapter()
    return OpenAICompatibleAdapter(
        os.environ.get("TRACEWITNESS_MODEL_BASE_URL", ""),
        os.environ.get("TRACEWITNESS_MODEL_API_KEY", ""),
        os.environ.get("TRACEWITNESS_MODEL_NAME", ""),
    )


adapter = build_adapter()


@app.get("/health")
def health():
    mock = isinstance(adapter, MockAdapter)
    return {
        "service": "ai",
        "mode": "MOCK" if mock else "DEMO",
        "banner": "MOCK_MODE" if mock else "MODEL_API",
    }


@app.post("/v1/agent/select-tool")
def select_tool_route(body: dict):
    def action():
        _check_fields(body, SELECT_TOOL_FIELDS)
        clean = dict(body)
        clean["task"] = _text(body, "task")
        clean["tools"] = _strings(body, "tools")
        clean["tool_results"] = _objects(body, "tool_results")
        clean["model_seed"] = _integer(body, "model_seed", 20261110)
        return select_tool(clean, adapter)

    return _call(action)


@app.post("/v1/agent/report")
def report_route(body: dict):
    def action():
        _check_fields(body, REPORT_FIELDS)
        clean = dict(body)
        clean["task"] = _text(body, "task")
        clean["tool_results"] = _objects(body, "tool_results")
        clean["model_seed"] = _integer(body, "model_seed", 20261110)
        clean["citation_hint"] = _text(body, "citation_hint")
        return draft_report(clean, adapter)

    return _call(action)


@app.post("/v1/vision/track")
def vision_track(body: dict):
    def action():
        _check_fields(body, TRACK_FIELDS)
        require_clock(body)
        frames = _integer(body, "frames", SAMPLE_FRAMES, 2, 30)
        width = _integer(body, "frame_width", 1920, 64, 4096)
        height = _integer(body, "frame_height", 1080, 64, 4096)
        return analyze_sample(frames, width, height)

    return _call(action)


@app.post("/v1/vision/structure")
def vision_structure(body: dict):
    def action():
        _check_fields(body, STRUCTURE_FIELDS)
        require_clock(body)
        case_key = _text(body, "case_key", "live")
        return structure_case(case_key, _points(body), _objects(body, "faults"))

    return _call(action)


def _call(action):
    try:
        return action()
    except (ModelNotConfigured, ModelCallFailed) as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
