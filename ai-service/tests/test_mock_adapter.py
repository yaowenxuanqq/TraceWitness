import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

from app.adapters import MockAdapter, OpenAICompatibleAdapter, draft_report, select_tool

CLOCK = {
    "run_id": "run-w02-mock-incident-20261110",
    "clock_mode": "EVAL",
    "request_timestamp": "2026-01-01T00:00:03.000Z",
    "tick": 3,
}

EVENTS = ["tw.event.run-w02-mock-incident-20261110.000001"]
TRACKS = ["tw.track.run-w02-mock-incident-20261110.000001"]
CLIPS = {EVENTS[0]: "tw.media.run-w02-mock-incident-20261110.000009"}


def test_mock_report_cites_only_tool_ids():
    report = draft_report(
        {
            **CLOCK,
            "task": "调查北门刚刚发生了什么",
            "tool_results": [
                {"tool_name": "search_events", "source_ids": ["tw.event.run-w02-mock-incident-20261110.000001"]}
            ],
        },
        MockAdapter(),
    )
    assert report["source_ids"] == ["tw.event.run-w02-mock-incident-20261110.000001"]
    assert report["insufficient_evidence"] is False
    assert report["conclusion"] == "北门摄像机记录到人员进入、停留后离开。"


def test_mock_report_without_ids_is_insufficient():
    report = draft_report({**CLOCK, "tool_results": [{"source_ids": []}]}, MockAdapter())
    assert report["insufficient_evidence"] is True
    assert report["source_ids"] == []
    assert report["conclusion"] == "证据不足"


def test_clock_is_required():
    try:
        select_tool({"task": "调查北门刚刚发生了什么"}, MockAdapter())
    except ValueError as exc:
        assert "request_timestamp" in str(exc)
    else:
        raise AssertionError("missing clock should fail")


def test_openai_adapter_posts_chat_completion():
    seen = {}

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length).decode("utf-8"))
            seen["body"] = body
            seen["auth"] = self.headers.get("Authorization")
            seen["path"] = self.path
            system = body["messages"][0]["content"]
            if system.startswith("根据"):
                message = {
                    "content": json.dumps(
                        {
                            "conclusion": "北门摄像机记录到人员进入、停留后离开。",
                            "source_ids": ["tw.event.run-w02-mock-incident-20261110.000001"],
                            "confidence": 0.8,
                            "insufficient_evidence": False,
                            "requested_actions": [],
                        },
                        ensure_ascii=False,
                    )
                }
            else:
                message = {
                    "tool_calls": [
                        {"function": {"name": "search_events", "arguments": "{\"limit\": 5}"}}
                    ]
                }
            payload = json.dumps({"choices": [{"message": message}]}, ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, fmt, *args):
            return

    server = HTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever)
    thread.start()
    try:
        port = server.server_address[1]
        adapter = OpenAICompatibleAdapter("http://127.0.0.1:%d/v1" % port, "local-key", "owner-model")
        selected = adapter.select_tool("调查北门刚刚发生了什么", ["search_events"], [], 20261110)
        assert selected["tool_calls"][0]["tool_name"] == "search_events"
        assert selected["tool_calls"][0]["arguments"] == {"limit": 5}
        assert seen["path"] == "/v1/chat/completions"
        assert seen["auth"] == "Bearer local-key"
        assert seen["body"]["temperature"] == 0
        assert seen["body"]["seed"] == 20261110
        assert seen["body"]["tools"][0]["function"]["name"] == "search_events"
        report = adapter.draft_report(
            "调查北门刚刚发生了什么",
            [{"source_ids": ["tw.event.run-w02-mock-incident-20261110.000001"]}],
            "",
            20261110,
        )
        assert report["insufficient_evidence"] is False
        assert report["source_ids"] == ["tw.event.run-w02-mock-incident-20261110.000001"]
    finally:
        server.shutdown()
        thread.join(timeout=3)


def _execute(call):
    name = call["tool_name"]
    args = call.get("arguments") or {}
    if name == "search_events":
        return {"tool_name": name, "source_ids": list(EVENTS)}
    if name == "get_event_detail":
        event_id = args.get("event_id")
        if event_id in EVENTS:
            return {"tool_name": name, "source_ids": [event_id]}
        return {"tool_name": name, "source_ids": [], "error": "event_not_found"}
    if name == "search_person":
        return {"tool_name": name, "source_ids": [], "people": [], "person_created": False}
    if name == "build_trajectory":
        if args.get("profile_id"):
            return {"tool_name": name, "source_ids": [], "tracks": []}
        return {"tool_name": name, "source_ids": list(TRACKS)}
    if name == "find_companions":
        return {"tool_name": name, "source_ids": [], "companions": []}
    if name == "export_evidence":
        exported = [CLIPS[item] for item in args.get("source_ids") or [] if item in CLIPS]
        result = {"tool_name": name, "source_ids": exported}
        if not exported:
            result["error"] = "evidence_missing"
        return result
    return {"tool_name": name, "source_ids": [], "error": "unknown_tool"}


def _run(task):
    adapter = MockAdapter()
    results = []
    seen = set()
    names = []
    for _ in range(4):
        plan = adapter.select_tool(task, [
            "search_events", "get_event_detail", "search_person",
            "build_trajectory", "find_companions", "export_evidence",
        ], results, 20261110)
        fresh = [call for call in plan["tool_calls"] if call["tool_name"] not in seen]
        if not fresh:
            break
        for call in fresh:
            seen.add(call["tool_name"])
            names.append(call["tool_name"])
            results.append(_execute(call))
    report = adapter.draft_report(task, results, "", 20261110)
    return names, results, report


def test_ten_positive_scenarios():
    path = Path(__file__).resolve().parents[2] / "eval" / "positive" / "w07.json"
    scenarios = json.loads(path.read_text(encoding="utf-8"))
    assert len(scenarios) == 10
    for scenario in scenarios:
        names, results, report = _run(scenario["task"])
        assert names == scenario["tools"], scenario["id"]
        assert report["insufficient_evidence"] is scenario["insufficient"], scenario["id"]
        if scenario["insufficient"]:
            assert report["conclusion"] == "证据不足"
            assert report["source_ids"] == []
        else:
            assert report["source_ids"]
        if scenario.get("cites_track"):
            assert TRACKS[0] in report["source_ids"]
        if scenario.get("cites_clip"):
            assert CLIPS[EVENTS[0]] in report["source_ids"]
        if "person_created" in scenario:
            assert results[-1]["person_created"] is scenario["person_created"]
            assert results[-1]["people"] == []
        if scenario.get("action"):
            assert report["requested_actions"][0]["action"] == scenario["action"]
            assert "尚未执行" in report["conclusion"]
