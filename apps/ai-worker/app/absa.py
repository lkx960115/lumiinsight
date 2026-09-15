"""整句情感 + 方面情感。方面名必须落在词典或「其它」。"""

from __future__ import annotations

import re
from typing import Any

from app.clean import match_aspects
from app.llm import add_usage, chat_json

CLAUSE_RE = re.compile(r"[，。；;,.!?！？]")

POS_WORDS = (
    "够用",
    "舒服",
    "简约",
    "挺搭",
    "清楚",
    "搞定",
    "完好",
    "扎实",
    "护眼",
    "还行",
    "及时",
    "好看",
    "不错",
    "满意",
    "推荐",
    "喜欢",
    "稳定",
)
NEG_WORDS = (
    "偏冷",
    "偏暖",
    "太暗",
    "太亮",
    "慢",
    "贵",
    "略贵",
    "失灵",
    "掉线",
    "闪烁",
    "差",
    "坏",
    "希望有",
    "偶发",
    "不满",
    "失望",
    "漏光",
)
NEGATORS = ("没有", "无", "不是")
ALLOWED_SENT = {"pos", "neg", "neu"}


def allowed_names(aspects: list[dict[str, Any]]) -> list[str]:
    names = [str(item.get("name") or "").strip() for item in aspects]
    names = [name for name in names if name]
    if "其它" not in names:
        names.append("其它")
    return names


def constrain_name(name: str, names: list[str]) -> str:
    raw = str(name or "").strip()
    if raw in names:
        return raw
    return "其它"


def clamp_conf(raw: Any, default: float = 0.6) -> float:
    try:
        value = float(raw)
    except (TypeError, ValueError):
        value = default
    return round(min(0.99, max(0.5, value)), 3)


def clause_of(text: str, keyword: str) -> str:
    parts = [part for part in CLAUSE_RE.split(text or "") if part]
    for part in parts:
        if keyword in part:
            return part
    return text or ""


def window_polarity(text: str, keyword: str) -> tuple[str, str, float]:
    chunk = clause_of(text, keyword)
    idx = chunk.find(keyword)
    if idx < 0:
        return "neu", "未写明倾向", 0.55
    prefix = chunk[:idx]
    pos_hit = next((word for word in POS_WORDS if word in chunk), None)
    neg_hit = next((word for word in NEG_WORDS if word in chunk), None)
    inverted = any(token in prefix for token in NEGATORS)
    if pos_hit and neg_hit:
        pos_at = chunk.find(pos_hit)
        neg_at = chunk.find(neg_hit)
        if abs(neg_at - idx) < abs(pos_at - idx):
            pos_hit = None
        else:
            neg_hit = None
    if pos_hit and not inverted:
        return "pos", pos_hit, 0.74
    if neg_hit and not inverted:
        return "neg", neg_hit, 0.74
    if inverted:
        return "pos", "否定问题表述", 0.68
    return "neu", "提到该方面但未写褒贬", 0.58


def rule_aspects(content: str, aspects: list[dict[str, Any]]) -> list[dict[str, Any]]:
    hits = match_aspects(content, aspects)
    items: list[dict[str, Any]] = []
    for name in hits:
        words = []
        for asp in aspects:
            if str(asp.get("name") or "").strip() != name:
                continue
            raw = str(asp.get("keywords") or "")
            words = [w.strip() for w in raw.split(",") if w.strip()]
            if name not in words:
                words.append(name)
        keyword = next((w for w in words if w and w in content), name)
        sentiment, reason, confidence = window_polarity(content, keyword)
        items.append(
            {
                "name": name,
                "sentiment": sentiment,
                "reason": reason,
                "confidence": confidence,
                "source": "rule",
            }
        )
    return items


def overall_from_aspects(aspects: list[dict[str, Any]], content: str) -> tuple[str, str, float]:
    if not aspects:
        pos = sum(1 for word in POS_WORDS if word in content)
        neg = sum(1 for word in NEG_WORDS if word in content)
        if pos > neg:
            return "pos", "原文偏正面", 0.6
        if neg > pos:
            return "neg", "原文偏负面", 0.6
        return "neu", "未识别到明确方面倾向", 0.55
    pos = sum(1 for item in aspects if item.get("sentiment") == "pos")
    neg = sum(1 for item in aspects if item.get("sentiment") == "neg")
    conf = sum(float(item.get("confidence") or 0.6) for item in aspects) / len(aspects)
    reasons = [str(item.get("name")) + str(item.get("reason") or "") for item in aspects[:3]]
    reason = "；".join(reasons)
    if pos > neg:
        return "pos", reason, round(conf, 3)
    if neg > pos:
        return "neg", reason, round(conf, 3)
    return "neu", reason, round(conf, 3)


def normalize_item(row: dict[str, Any], names: list[str], source: str) -> dict[str, Any]:
    aspects_in = row.get("aspects") if isinstance(row.get("aspects"), list) else []
    aspects: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in aspects_in:
        if not isinstance(item, dict):
            continue
        name = constrain_name(str(item.get("name") or ""), names)
        if name in seen:
            continue
        seen.add(name)
        sent = str(item.get("sentiment") or "neu")
        if sent not in ALLOWED_SENT:
            sent = "neu"
        aspects.append(
            {
                "name": name,
                "sentiment": sent,
                "reason": str(item.get("reason") or "")[:120],
                "confidence": clamp_conf(item.get("confidence")),
                "source": source,
            }
        )
    sentiment = str(row.get("sentiment") or "neu")
    if sentiment not in ALLOWED_SENT:
        sentiment = "neu"
    return {
        "id": row.get("id"),
        "sentiment": sentiment,
        "reason": str(row.get("reason") or "")[:120],
        "confidence": clamp_conf(row.get("confidence")),
        "aspects": aspects,
        "source": source,
    }


def _llm_prompt(batch: list[dict[str, Any]], names: list[str]) -> str:
    lines = [
        "你是灯具评论分析器。只根据给定原文判断，不要编造原文没有的事实。",
        "方面名必须从下列选择，禁止自创近义词：" + "、".join(names),
        "情感只能是 pos、neg、neu。",
        '输出 JSON：{"items":[{"id":1,"sentiment":"pos","confidence":0.8,"reason":"整句摘要","aspects":[{"name":"亮度","sentiment":"pos","reason":"够用","confidence":0.8}]}]}',
        "评论：",
    ]
    for row in batch:
        lines.append("[%s] %s" % (row.get("id"), row.get("content") or ""))
    return "\n".join(lines)


def unique_reviews(reviews: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen: set[str] = set()
    unique: list[dict[str, Any]] = []
    for row in reviews:
        key = str(row.get("content") or "").strip()
        if key in seen:
            continue
        seen.add(key)
        unique.append(row)
    return unique


def copy_by_content(source: dict[Any, dict[str, Any]], reviews: list[dict[str, Any]]) -> dict[Any, dict[str, Any]]:
    by_content: dict[str, dict[str, Any]] = {}
    for row in reviews:
        rid = row.get("id")
        if rid in source:
            by_content[str(row.get("content") or "").strip()] = source[rid]
    out = dict(source)
    for row in reviews:
        rid = row.get("id")
        if rid in out:
            continue
        template = by_content.get(str(row.get("content") or "").strip())
        if not template:
            continue
        copied = dict(template)
        copied["id"] = rid
        copied["aspects"] = [dict(item) for item in (template.get("aspects") or [])]
        out[rid] = copied
    return out


def _analyze_with_llm(reviews: list[dict[str, Any]], names: list[str], spec: dict[str, Any]) -> tuple[dict[Any, dict[str, Any]], dict[str, int]]:
    out: dict[Any, dict[str, Any]] = {}
    usage = {"promptTokens": 0, "completionTokens": 0, "totalTokens": 0}
    batch_size = 5
    unique = unique_reviews(reviews)
    for i in range(0, len(unique), batch_size):
        batch = unique[i : i + batch_size]
        try:
            data, batch_usage = chat_json(spec, _llm_prompt(batch, names))
            usage = add_usage(usage, batch_usage)
        except Exception:
            continue
        items = data.get("items") if isinstance(data.get("items"), list) else []
        for item in items:
            if not isinstance(item, dict) or item.get("id") is None:
                continue
            out[item.get("id")] = normalize_item(item, names, "llm")
    return copy_by_content(out, reviews), usage


def analyze_reviews(
    reviews: list[dict[str, Any]],
    aspects: list[dict[str, Any]] | None = None,
    llm: dict[str, Any] | None = None,
) -> dict[str, Any]:
    dicts = aspects or []
    names = allowed_names(dicts)
    llm_items: dict[Any, dict[str, Any]] = {}
    llm_error = ""
    source_used = "rule"
    usage = {"promptTokens": 0, "completionTokens": 0, "totalTokens": 0}
    if llm and isinstance(llm, dict) and llm.get("apiKey"):
        try:
            llm_items, usage = _analyze_with_llm(reviews, names, llm)
            if llm_items:
                source_used = "llm"
        except Exception as exc:
            llm_error = str(exc)
            llm_items = {}

    items: list[dict[str, Any]] = []
    aspect_count = 0
    for row in reviews:
        rid = row.get("id")
        content = str(row.get("content") or "")
        chosen = llm_items.get(rid)
        if chosen is None or not chosen.get("aspects"):
            rule = rule_aspects(content, dicts)
            sentiment, reason, confidence = overall_from_aspects(rule, content)
            chosen = {
                "id": rid,
                "sentiment": sentiment,
                "reason": reason,
                "confidence": confidence,
                "aspects": rule,
                "source": "rule",
            }
            if llm_items.get(rid) and not llm_items[rid].get("aspects"):
                chosen["source"] = "llm+rule"
        items.append(chosen)
        aspect_count += len(chosen.get("aspects") or [])

    if source_used == "llm":
        how = "模型 JSON"
    elif llm_error:
        how = "词典规则（模型调用失败已降级）"
    elif llm:
        how = "词典规则（模型未返回有效结果）"
    else:
        how = "词典规则（未配置模型 Key）"
    message = "分析完成：%s 条，方面 %s 条（%s）" % (len(items), aspect_count, how)
    return {
        "items": items,
        "message": message,
        "analyzed": len(items),
        "aspectCount": aspect_count,
        "source": source_used if not llm_error else "rule",
        "usage": usage,
    }
