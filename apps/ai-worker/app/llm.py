"""OpenAI 兼容 Chat Completions。Key 只用于请求头，不写日志。"""

from __future__ import annotations

import json
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def chat_json(spec: dict[str, Any], user_content: str) -> dict[str, Any]:
    base = str(spec.get("baseUrl") or "").rstrip("/")
    model = str(spec.get("model") or "").strip()
    api_key = str(spec.get("apiKey") or "").strip()
    if not base or not model or not api_key:
        raise RuntimeError("模型配置不完整")
    timeout_ms = spec.get("timeoutMs") or 60000
    timeout = max(5, int(timeout_ms) / 1000)
    messages = [
        {
            "role": "system",
            "content": "只输出 JSON 对象，不要 Markdown。",
        },
        {"role": "user", "content": user_content},
    ]
    payload = {
        "model": model,
        "messages": messages,
        "temperature": 0.1,
    }
    json_mode = spec.get("jsonMode")
    if json_mode is None or int(json_mode) == 1:
        payload["response_format"] = {"type": "json_object"}
    raw = _post(base + "/chat/completions", api_key, payload, timeout, spec.get("extraHeaders"))
    content = _message_content(raw)
    return _parse_json_object(content)


def _post(url: str, api_key: str, payload: dict[str, Any], timeout: float, extra_headers: Any) -> dict[str, Any]:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "Authorization": "Bearer " + api_key,
    }
    if isinstance(extra_headers, str) and extra_headers.strip():
        try:
            extra_headers = json.loads(extra_headers)
        except json.JSONDecodeError:
            extra_headers = None
    if isinstance(extra_headers, dict):
        for key, value in extra_headers.items():
            if str(key).lower() in {"authorization", "content-type"}:
                continue
            headers[str(key)] = str(value)
    req = Request(url, data=body, headers=headers, method="POST")
    try:
        with urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8") or "{}")
    except HTTPError as exc:
        raise RuntimeError("模型接口 HTTP %s" % exc.code) from None
    except URLError:
        raise RuntimeError("模型接口不可达") from None


def _message_content(raw: dict[str, Any]) -> str:
    choices = raw.get("choices") or []
    if not choices:
        raise RuntimeError("模型未返回内容")
    message = (choices[0] or {}).get("message") or {}
    content = message.get("content")
    if isinstance(content, list):
        parts = [str(part.get("text") or "") for part in content if isinstance(part, dict)]
        content = "".join(parts)
    text = str(content or "").strip()
    if not text:
        raise RuntimeError("模型返回空内容")
    return text


def _parse_json_object(text: str) -> dict[str, Any]:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.strip("`")
        if cleaned.startswith("json"):
            cleaned = cleaned[4:]
        cleaned = cleaned.strip()
    start = cleaned.find("{")
    end = cleaned.rfind("}")
    if start < 0 or end <= start:
        raise RuntimeError("模型未返回 JSON")
    data = json.loads(cleaned[start : end + 1])
    if not isinstance(data, dict):
        raise RuntimeError("模型 JSON 不是对象")
    return data
