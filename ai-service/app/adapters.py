"""Model adapters.

MockAdapter is used when the owner has not configured an API. OpenAICompatibleAdapter
posts to the configured OpenAI-compatible chat endpoint. Business time stays with the caller.
"""

import json
from pathlib import Path
from typing import Optional, Protocol
from urllib import error, request

from jsonschema import Draft202012Validator

P0_TOOLS = (
    "search_events",
    "get_event_detail",
    "search_person",
    "build_trajectory",
    "find_companions",
    "export_evidence",
)

TOOL_PARAMETERS = {
    "search_events": {
        "type": "object",
        "additionalProperties": False,
        "properties": {"limit": {"type": "integer", "minimum": 1, "maximum": 20}},
    },
    "get_event_detail": {
        "type": "object",
        "additionalProperties": False,
        "required": ["event_id"],
        "properties": {"event_id": {"type": "string"}},
    },
    "search_person": {
        "type": "object",
        "additionalProperties": False,
        "properties": {"query": {"type": "string"}},
    },
    "build_trajectory": {
        "type": "object",
        "additionalProperties": False,
        "properties": {"profile_id": {"type": "string"}},
    },
    "find_companions": {
        "type": "object",
        "additionalProperties": False,
        "properties": {"profile_id": {"type": "string"}},
    },
    "export_evidence": {
        "type": "object",
        "additionalProperties": False,
        "properties": {"source_ids": {"type": "array", "items": {"type": "string"}}},
    },
}

CLOCK_FIELDS = ("run_id", "clock_mode", "request_timestamp", "tick")


class ModelNotConfigured(Exception):
    pass


class ModelCallFailed(Exception):
    pass


class ModelAdapter(Protocol):
    def select_tool(self, task: str, tools: list[str], tool_results: list[dict], model_seed: int) -> dict:
        pass

    def draft_report(self, task: str, tool_results: list[dict], citation_hint: str, model_seed: int) -> dict:
        pass


class MockAdapter:
    def select_tool(self, task: str, tools: list[str], tool_results: Optional[list] = None, model_seed: int = 20261110) -> dict:
        done = {item.get("tool_name") for item in (tool_results or [])}
        ids = _source_ids(tool_results or [])
        calls: list[dict] = []

        def add(name: str, arguments: dict) -> None:
            if name in tools and name not in done:
                calls.append({"tool_name": name, "arguments": arguments})

        if "同伴" in task or "同行" in task:
            add("find_companions", {})
        elif "人员" in task and "事件" not in task:
            add("search_person", {"query": task})
        elif "不存在" in task:
            add("get_event_detail", {"event_id": "tw.event.missing.000001"})
        elif "列表" in task:
            add("search_events", {"limit": 5})
        elif "导出" in task:
            add("search_events", {"limit": 5})
            if ids:
                add("export_evidence", {"source_ids": ids[:1]})
        elif "轨迹" in task:
            add("search_events", {"limit": 5})
            if ids:
                add("build_trajectory", {})
        else:
            add("search_events", {"limit": 5})
            if ids:
                add("get_event_detail", {"event_id": ids[0]})
        return {"tool_calls": calls}

    def draft_report(self, task: str, tool_results: list[dict], citation_hint: str = "", model_seed: int = 20261110) -> dict:
        source_ids = _source_ids(tool_results)
        if not source_ids:
            return _insufficient()
        actions: list[dict] = []
        if "开门" in task or "门禁" in task:
            actions.append({"action": "door_open"})
            conclusion = "门禁动作已提交人工审批，尚未执行。"
        elif "北门" in task:
            conclusion = "北门摄像机记录到人员进入、停留后离开。"
        else:
            conclusion = "结论仅依据已返回的证据。"
        return {
            "conclusion": conclusion,
            "source_ids": source_ids,
            "confidence": 0.8,
            "insufficient_evidence": False,
            "requested_actions": actions,
        }


class OpenAICompatibleAdapter:
    def __init__(self, base_url: str, api_key: str, model: str) -> None:
        self.base_url = (base_url or "").rstrip("/")
        self.api_key = api_key or ""
        self.model = model or ""

    def select_tool(self, task: str, tools: list[str], tool_results: Optional[list] = None, model_seed: int = 20261110) -> dict:
        self._configured()
        allowed = [name for name in tools if name in TOOL_PARAMETERS]
        payload = {
            "model": self.model,
            "temperature": 0,
            "seed": int(model_seed),
            "messages": [
                {
                    "role": "system",
                    "content": "只从给定工具里选择下一步。已经执行过的工具不要重复。没有更多工具可调用时，返回空的 tool_calls。不要编造证据 ID。",
                },
                {
                    "role": "user",
                    "content": json.dumps({"task": task, "tool_results": tool_results or []}, ensure_ascii=False),
                },
            ],
            "tools": [_function(name) for name in allowed],
            "tool_choice": "auto",
        }
        message = self._message(payload)
        return {"tool_calls": _parse_tool_calls(message, allowed)}

    def draft_report(self, task: str, tool_results: list[dict], citation_hint: str = "", model_seed: int = 20261110) -> dict:
        self._configured()
        payload = {
            "model": self.model,
            "temperature": 0,
            "seed": int(model_seed),
            "messages": [
                {
                    "role": "system",
                    "content": (
                        "根据工具结果写调查报告，只返回一个 JSON 对象，键为 conclusion、source_ids、confidence、"
                        "insufficient_evidence、requested_actions。source_ids 只能取自工具结果。没有可用 ID 时，"
                        "conclusion 必须是“证据不足”，source_ids 为空，insufficient_evidence 为 true。"
                        "门禁和梯控只能放进 requested_actions，不能声称已经执行。"
                    ),
                },
                {
                    "role": "user",
                    "content": json.dumps(
                        {"task": task, "tool_results": tool_results, "citation_hint": citation_hint or ""},
                        ensure_ascii=False,
                    ),
                },
            ],
        }
        message = self._message(payload)
        return validate_report(_parse_json_content(message))

    def _configured(self) -> None:
        if not self.base_url or not self.api_key or not self.model:
            raise ModelNotConfigured("模型 API 未配置")

    def _message(self, payload: dict) -> dict:
        url = self.base_url + "/chat/completions"
        if not self.base_url.endswith("/v1"):
            url = self.base_url + "/v1/chat/completions"
        body = json.dumps(payload).encode("utf-8")
        req = request.Request(
            url,
            data=body,
            headers={"Authorization": "Bearer " + self.api_key, "Content-Type": "application/json"},
            method="POST",
        )
        try:
            with request.urlopen(req, timeout=30) as response:
                data = json.loads(response.read().decode("utf-8"))
        except error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise ModelCallFailed("model http " + str(exc.code) + " " + detail[:300]) from exc
        except error.URLError as exc:
            raise ModelCallFailed("model unreachable") from exc
        try:
            return data["choices"][0]["message"]
        except (KeyError, IndexError, TypeError) as exc:
            raise ModelCallFailed("model response missing message") from exc


def require_clock(body: dict) -> None:
    missing = [field for field in CLOCK_FIELDS if body.get(field) is None]
    if missing:
        raise ValueError("missing clock fields: " + ", ".join(missing))
    if not isinstance(body["run_id"], str) or not body["run_id"].strip():
        raise ValueError("run_id must be a non-empty string")
    if not isinstance(body["request_timestamp"], str) or not body["request_timestamp"].strip():
        raise ValueError("request_timestamp must be a non-empty string")
    if isinstance(body["tick"], bool) or not isinstance(body["tick"], int) or body["tick"] < 0:
        raise ValueError("tick must be a non-negative integer")
    if body["clock_mode"] not in ("EVAL", "REAL"):
        raise ValueError("clock_mode must be EVAL or REAL")


def select_tool(body: dict, adapter: ModelAdapter) -> dict:
    require_clock(body)
    plan = adapter.select_tool(
        body.get("task") or "",
        list(body.get("tools") or []),
        list(body.get("tool_results") or []),
        int(body.get("model_seed") or 20261110),
    )
    return validate_tool_calls(plan)


def validate_tool_calls(plan: dict) -> dict:
    if not isinstance(plan, dict):
        raise ValueError("tool plan must be an object")
    calls = plan.get("tool_calls") or []
    if not isinstance(calls, list):
        raise ValueError("tool_calls must be a list")
    for call in calls:
        name = call.get("tool_name")
        schema = TOOL_PARAMETERS.get(name)
        if schema is None:
            raise ValueError("unknown tool " + str(name))
        arguments = call.get("arguments") or {}
        errors = sorted(Draft202012Validator(schema).iter_errors(arguments), key=lambda item: list(item.path))
        if errors:
            raise ValueError("tool arguments: " + errors[0].message)
    return plan


def draft_report(body: dict, adapter: ModelAdapter) -> dict:
    require_clock(body)
    report = adapter.draft_report(
        body.get("task") or "",
        list(body.get("tool_results") or []),
        body.get("citation_hint") or "",
        int(body.get("model_seed") or 20261110),
    )
    return validate_report(report)


def validate_report(report: dict) -> dict:
    if not isinstance(report, dict):
        raise ValueError("report must be an object")
    schema_path = Path(__file__).resolve().parents[2] / "contracts" / "agent-report.schema.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    errors = sorted(Draft202012Validator(schema).iter_errors(report), key=lambda item: list(item.path))
    if errors:
        raise ValueError("report schema: " + errors[0].message)
    if not report["source_ids"] and not report["insufficient_evidence"]:
        raise ValueError("empty source_ids require insufficient_evidence")
    return report


def _function(name: str) -> dict:
    return {
        "type": "function",
        "function": {"name": name, "parameters": TOOL_PARAMETERS[name]},
    }


def _source_ids(tool_results: list[dict]) -> list[str]:
    source_ids: list[str] = []
    for result in tool_results:
        for source_id in result.get("source_ids") or []:
            if source_id not in source_ids:
                source_ids.append(source_id)
    return source_ids


def _insufficient() -> dict:
    return {
        "conclusion": "证据不足",
        "source_ids": [],
        "confidence": 0.0,
        "insufficient_evidence": True,
        "requested_actions": [],
    }


def _parse_tool_calls(message: dict, allowed: list[str]) -> list[dict]:
    calls = []
    for item in message.get("tool_calls") or []:
        function = item.get("function") or {}
        name = function.get("name")
        if name not in allowed:
            continue
        raw = function.get("arguments") or "{}"
        arguments = json.loads(raw) if isinstance(raw, str) else raw
        if not isinstance(arguments, dict):
            arguments = {}
        calls.append({"tool_name": name, "arguments": arguments})
    if calls:
        return calls
    content = message.get("content")
    if not content:
        return []
    parsed = _parse_json_content(message)
    if isinstance(parsed, dict):
        raw_calls = parsed.get("tool_calls") or []
        if isinstance(raw_calls, list):
            return [
                {"tool_name": item.get("tool_name"), "arguments": item.get("arguments") or {}}
                for item in raw_calls
                if isinstance(item, dict) and item.get("tool_name") in allowed
            ]
    return []


def _parse_json_content(message: dict) -> dict:
    if message.get("tool_calls"):
        return {}
    content = message.get("content") or ""
    text = content.strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.startswith("json"):
            text = text[4:]
        text = text.strip()
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError as exc:
        raise ModelCallFailed("model content is not json") from exc
    if not isinstance(parsed, dict):
        raise ModelCallFailed("model content is not an object")
    return parsed
