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
    return _call(lambda: select_tool(body, adapter))


@app.post("/v1/agent/report")
def report_route(body: dict):
    return _call(lambda: draft_report(body, adapter))


@app.post("/v1/vision/track")
def vision_track(body: dict):
    def action():
        require_clock(body)
        frames = int(body.get("frames") or SAMPLE_FRAMES)
        width = int(body.get("frame_width") or 1920)
        height = int(body.get("frame_height") or 1080)
        return analyze_sample(frames, width, height)

    return _call(action)


@app.post("/v1/vision/structure")
def vision_structure(body: dict):
    def action():
        require_clock(body)
        case_key = body.get("case_key") or "live"
        return structure_case(case_key, body.get("points") or [], body.get("faults") or [])

    return _call(action)


def _call(action):
    try:
        return action()
    except (ModelNotConfigured, ModelCallFailed) as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
