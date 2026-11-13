import hmac
import os

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse

from app.adapters import (
    MockAdapter,
    ModelCallFailed,
    ModelNotConfigured,
    OpenAICompatibleAdapter,
    draft_report,
    require_clock,
    select_tool,
)
from app.attributes import structure_case
from app.vision import SAMPLE_FRAMES, analyze_sample

app = FastAPI(title="TraceWitness AI")

SELECT_TOOL_FIELDS = frozenset(
    {
        "run_id",
        "clock_mode",
        "request_timestamp",
        "tick",
        "task",
        "tools",
        "tool_results",
        "model_seed",
    }
)
REPORT_FIELDS = frozenset(
    {
        "run_id",
        "clock_mode",
        "request_timestamp",
        "tick",
        "task",
        "tool_results",
        "model_seed",
        "citation_hint",
    }
)
TRACK_FIELDS = frozenset(
    {
        "run_id",
        "clock_mode",
        "request_timestamp",
        "tick",
        "frames",
        "frame_width",
        "frame_height",
    }
)
STRUCTURE_FIELDS = frozenset(
    {
        "run_id",
        "clock_mode",
        "request_timestamp",
        "tick",
        "case_key",
        "points",
        "faults",
    }
)
POINT_FIELDS = ("frame_index", "tick", "x", "y", "w", "h", "confidence")
MAX_FRAME_PIXELS = 4096 * 4096
MAX_TOTAL_PIXELS = 50_000_000


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
                raise ValueError(f"points[{index}] missing {field}")
        for field in ("frame_index", "tick"):
            if isinstance(point[field], bool) or not isinstance(point[field], int):
                raise ValueError(f"points[{index}].{field} must be an integer")
        for field in ("x", "y", "w", "h", "confidence"):
            if isinstance(point[field], bool) or not isinstance(point[field], (int, float)):
                raise ValueError(f"points[{index}].{field} must be a number")
    if len(points) > 10000:
        raise ValueError("points is too large")
    return points


def build_adapter():
    mode = os.environ.get("TRACEWITNESS_MODE", "MOCK")
    if mode.upper() == "MOCK" or os.environ.get("MOCK_MODE") == "1":
        return MockAdapter()
    allow_empty_key = os.environ.get("TRACEWITNESS_MODEL_ALLOW_EMPTY_KEY") == "1"
    return OpenAICompatibleAdapter(
        os.environ.get("TRACEWITNESS_MODEL_BASE_URL", ""),
        os.environ.get("TRACEWITNESS_MODEL_API_KEY", ""),
        os.environ.get("TRACEWITNESS_MODEL_NAME", ""),
        allow_empty_key=allow_empty_key,
    )


def _assert_service_token():
    mode = os.environ.get("TRACEWITNESS_MODE", "MOCK").upper()
    mock = mode == "MOCK" or os.environ.get("MOCK_MODE") == "1"
    token = os.environ.get("TRACEWITNESS_SERVICE_TOKEN", "")
    if not mock and not token:
        raise RuntimeError("TRACEWITNESS_SERVICE_TOKEN is required when TRACEWITNESS_MODE is not MOCK")


def require_service_token(authorization: str | None) -> None:
    expected = os.environ.get("TRACEWITNESS_SERVICE_TOKEN", "")
    presented = ""
    if isinstance(authorization, str) and authorization.lower().startswith("bearer "):
        presented = authorization[7:].strip()
    if not expected or not hmac.compare_digest(presented.encode("utf-8"), expected.encode("utf-8")):
        raise HTTPException(status_code=401, detail="unauthorized")


adapter = build_adapter()


@app.middleware("http")
async def service_auth(request, call_next):
    if request.url.path.startswith("/v1/"):
        try:
            require_service_token(request.headers.get("authorization"))
        except HTTPException as exc:
            return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})
    return await call_next(request)


@app.on_event("startup")
def _startup():
    _assert_service_token()


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
        pixels = frames * width * height
        if width * height > MAX_FRAME_PIXELS or pixels > MAX_TOTAL_PIXELS:
            raise ValueError("frame pixel budget exceeded")
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
