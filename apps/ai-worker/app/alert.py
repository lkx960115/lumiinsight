"""口碑预警说明：只根据月份对照和原评写一句，不编。"""

from __future__ import annotations

from typing import Any

from app.llm import chat_json

ALERT_RULES = """你是灯鉴口碑顾问。下面是按评论时间对照出来的负向变多，以及对应原评。
规则：
1. 每个方面写一句中文：哪方面、哪段时间、负向从多少条变成多少条，并点到原评里的说法。
2. evidenceIds 只能用该方面给出的 id。不许编销量、型号或未出现的事实。
3. 只输出 JSON 对象：{"items":[{"aspect":"安装","summary":"中文","evidenceIds":[id,...]}]}，不要 Markdown。
4. 方面名必须和输入一致，不要发明新方面。
"""


def write_alerts(items: list[dict[str, Any]], spec: dict[str, Any] | None) -> dict[str, Any]:
    prepared = []
    for row in items:
        if not isinstance(row, dict):
            continue
        aspect = str(row.get("aspect") or "").strip()
        if not aspect:
            continue
        allowed, lines = snippet_lines(row.get("snippets") or [])
        prepared.append(
            {
                "aspect": aspect,
                "recentLabel": str(row.get("recentLabel") or ""),
                "previousLabel": str(row.get("previousLabel") or ""),
                "recentNeg": int(row.get("recentNeg") or 0),
                "previousNeg": int(row.get("previousNeg") or 0),
                "allowed": allowed,
                "lines": lines,
                "fallback": fallback_summary(row, allowed, lines),
            }
        )
    if not prepared:
        return {"items": [], "source": "empty", "usage": {}}
    if not spec or not spec.get("apiKey"):
        return {
            "items": [pack(row, row["fallback"], row["allowed"][:3], "rule") for row in prepared],
            "source": "rule",
            "usage": {},
        }
    prompt = ALERT_RULES + "\n对照：\n" + "\n\n".join(describe(row) for row in prepared)
    try:
        data, usage = chat_json(spec, prompt)
    except Exception:
        return {
            "items": [pack(row, row["fallback"], row["allowed"][:3], "rule") for row in prepared],
            "source": "rule",
            "usage": {},
        }
    by_aspect = {}
    raw_items = data.get("items") if isinstance(data, dict) else None
    if isinstance(raw_items, list):
        for item in raw_items:
            if isinstance(item, dict) and item.get("aspect"):
                by_aspect[str(item.get("aspect")).strip()] = item
    out = []
    for row in prepared:
        llm_row = by_aspect.get(row["aspect"]) or {}
        summary = str(llm_row.get("summary") or "").strip()
        ids = filter_ids(llm_row.get("evidenceIds"), row["allowed"])
        if not summary:
            summary = row["fallback"]
            ids = row["allowed"][:3]
            out.append(pack(row, summary, ids, "rule"))
        else:
            if not ids:
                ids = row["allowed"][:3]
            out.append(pack(row, summary, ids[:5], "llm"))
    source = "llm" if any(item.get("source") == "llm" for item in out) else "rule"
    return {"items": out, "source": source, "usage": usage}


def fallback_summary(row: dict[str, Any], allowed: list[int], lines: list[str]) -> str:
    quote = ""
    if lines:
        text = lines[0].split("\n", 1)[-1].strip()
        quote = text[:24] + ("…" if len(text) > 24 else "")
    aspect = str(row.get("aspect") or "")
    recent = str(row.get("recentLabel") or "最近")
    previous = str(row.get("previousLabel") or "上一期")
    recent_neg = int(row.get("recentNeg") or 0)
    previous_neg = int(row.get("previousNeg") or 0)
    if quote:
        return "%s「%s」负向 %s 条，%s %s 条。原评写到「%s」。" % (
            recent,
            aspect,
            recent_neg,
            previous,
            previous_neg,
            quote,
        )
    return "%s「%s」负向 %s 条，%s %s 条。请核对原评。" % (
        recent,
        aspect,
        recent_neg,
        previous,
        previous_neg,
    )


def snippet_lines(snippets: Any) -> tuple[list[int], list[str]]:
    allowed: list[int] = []
    lines: list[str] = []
    if not isinstance(snippets, list):
        return allowed, lines
    for row in snippets:
        if not isinstance(row, dict) or row.get("id") is None:
            continue
        try:
            rid = int(row.get("id"))
        except (TypeError, ValueError):
            continue
        allowed.append(rid)
        content = str(row.get("content") or row.get("quote") or "").strip().replace("\n", " ")
        if len(content) > 180:
            content = content[:180] + "…"
        lines.append("[%s]\n%s" % (rid, content))
    return allowed, lines


def describe(row: dict[str, Any]) -> str:
    body = "\n".join(row["lines"]) or "（无原评）"
    return "方面：%s\n%s 负向 %s 条；%s 负向 %s 条\n原评：\n%s" % (
        row["aspect"],
        row["recentLabel"],
        row["recentNeg"],
        row["previousLabel"],
        row["previousNeg"],
        body,
    )


def filter_ids(raw: Any, allowed: list[int]) -> list[int]:
    ids: list[int] = []
    if not isinstance(raw, list):
        return ids
    for item in raw:
        try:
            value = int(item)
        except (TypeError, ValueError):
            continue
        if value in allowed and value not in ids:
            ids.append(value)
    return ids


def pack(row: dict[str, Any], summary: str, ids: list[int], source: str) -> dict[str, Any]:
    return {
        "aspect": row["aspect"],
        "summary": summary,
        "evidenceIds": ids,
        "source": source,
    }
