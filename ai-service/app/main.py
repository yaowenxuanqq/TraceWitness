import os

from fastapi import FastAPI, HTTPException

from app.adapters import (
    MockAdapter,
    ModelNotConfigured,
    OpenAICompatibleAdapter,
    draft_report,
    select_tool,
)

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


def _call(action):
    try:
        return action()
    except ModelNotConfigured as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
