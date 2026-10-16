"""问原评：只根据给定原评作答，无证据不编。"""

from __future__ import annotations

from typing import Any

from app.llm import chat_json

ASK_RULES = """你是灯鉴口碑顾问。只根据下列原评回答用户问题。
规则：
1. 先给结论，再点到原评。不要寒暄。
2. 每句判断都必须能在原评里找到依据。evidenceIds 只能用下面给出的 id。
3. 原评不够就回答：这批评论里还没有足够原评来回答。evidenceIds 为空数组。
4. 不许用训练知识补充竞品事实、销量、行业常识或未出现的型号。
5. 方面名只用原评里出现的（如亮度、色温、安装、售后、价格、物流、频闪），不要发明新方面。
6. 只输出 JSON 对象：{"answer":"中文结论","evidenceIds":[id,...]}，不要 Markdown。
"""


def answer_question(question: str, snippets: list[dict[str, Any]], spec: dict[str, Any] | None) -> dict[str, Any]:
    allowed = []
    lines = []
    for row in snippets:
        if not isinstance(row, dict) or row.get("id") is None:
            continue
        try:
            rid = int(row.get("id"))
        except (TypeError, ValueError):
            continue
        allowed.append(rid)
        aspect = str(row.get("aspect") or "").strip()
        sentiment = str(row.get("sentiment") or "").strip()
        platform = str(row.get("platform") or "").strip()
        content = str(row.get("content") or row.get("quote") or "").strip().replace("\n", " ")
        if len(content) > 180:
            content = content[:180] + "…"
        meta = " · ".join([part for part in (platform, sentiment, aspect) if part])
        lines.append("[%s] %s\n%s" % (rid, meta, content))
    if not allowed:
        return {
            "answer": "这批评论里还没有足够原评来回答。请先导入并完成清洗并分析。",
            "evidenceIds": [],
            "source": "empty",
            "usage": {},
        }
    if not spec or not spec.get("apiKey"):
        return extractive(question, snippets, allowed)
    prompt = ASK_RULES + "\n问题：%s\n\n原评：\n%s" % (question.strip(), "\n\n".join(lines))
    try:
        data, usage = chat_json(spec, prompt)
    except Exception:
        return extractive(question, snippets, allowed)
    ids = []
    raw_ids = data.get("evidenceIds") if isinstance(data, dict) else None
    if isinstance(raw_ids, list):
        for item in raw_ids:
            try:
                value = int(item)
            except (TypeError, ValueError):
                continue
            if value in allowed and value not in ids:
                ids.append(value)
    answer = str((data or {}).get("answer") or "").strip()
    if not answer:
        return extractive(question, snippets, allowed)
    if not ids and "还没有" not in answer:
        ids = allowed[:3]
    return {"answer": answer, "evidenceIds": ids[:5], "source": "llm", "usage": usage}


def extractive(question: str, snippets: list[dict[str, Any]], allowed: list[int]) -> dict[str, Any]:
    first = next((row for row in snippets if isinstance(row, dict) and row.get("id") is not None), None)
    quote = ""
    if first:
        quote = str(first.get("content") or first.get("quote") or "").strip()
        if len(quote) > 42:
            quote = quote[:42] + "…"
    if not quote:
        return {
            "answer": "这批评论里还没有足够原评来回答。",
            "evidenceIds": [],
            "source": "rule",
            "usage": {},
        }
    return {
        "answer": "根据原评，「%s」。下面可核对原文；未配置问答模型时只摘原评，不补充判断。" % quote,
        "evidenceIds": allowed[:3],
        "source": "rule",
        "usage": {},
    }
