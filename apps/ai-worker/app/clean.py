"""评论清洗：去重、广告、过短、模板好评、脱敏，并按当前方面词典打方面词。"""

from __future__ import annotations

import re
from typing import Any

PHONE_RE = re.compile(r"1[3-9]\d{9}")
ADDRESS_RE = re.compile(r"([\u4e00-\u9fa5]{2,}(?:省|市|区|县|镇|路|街|号).{0,20})")
CORE_RE = re.compile(r"[\s\u3000\W_]+", re.UNICODE)

AD_KEYWORDS = (
    "加微信",
    "加微",
    "加vx",
    "加VX",
    "加v信",
    "加威信",
    "咨询电话",
    "优惠券",
    "代购",
    "免费领取",
    "点击链接",
    "淘口令",
    "私聊同款",
    "同款加",
    "扫码领",
    "代下单",
)

TEMPLATE_EXACT = {"不错", "很好", "可以", "好评", "满意", "推荐", "五星好评"}
TEMPLATE_PHRASES = ("好评", "五星", "非常满意", "还会再来", "不错不错", "推荐购买")


def mask_text(content: str) -> tuple[str, bool]:
    text = content or ""
    masked = PHONE_RE.sub("1**********", text)
    masked = ADDRESS_RE.sub("[地址已脱敏]", masked)
    return masked, masked != text


def is_ad(content: str) -> bool:
    text = content or ""
    lowered = text.lower()
    if any(word in text or word.lower() in lowered for word in AD_KEYWORDS):
        return True
    if re.search(r"(wx|weixin|v信|威信)\s*[:：]", text, re.I):
        return True
    return False


def is_short(content: str) -> bool:
    core = CORE_RE.sub("", content or "")
    return len(core) < 4


def template_reason(content: str) -> str | None:
    core = CORE_RE.sub("", content or "")
    if core in TEMPLATE_EXACT:
        return "全文是模板好评「%s」" % core
    hits = [p for p in TEMPLATE_PHRASES if p in (content or "")]
    if len(hits) >= 2 and len(core) <= 24:
        return "堆砌模板好评：" + "、".join(hits)
    return None


def match_aspects(content: str, aspects: list[dict[str, Any]]) -> list[str]:
    text = content or ""
    hits: list[str] = []
    for asp in aspects:
        name = str(asp.get("name") or "").strip()
        if not name or name == "其它":
            continue
        raw = asp.get("keywords")
        words = [w.strip() for w in str(raw or "").split(",") if w.strip()]
        if name not in words:
            words.append(name)
        if any(word and word in text for word in words):
            hits.append(name)
    return hits


def explain(tags: list[str], template: str | None) -> str:
    parts: list[str] = []
    if "duplicate" in tags:
        parts.append("与同平台已出现的原文重复")
    if "ad" in tags:
        parts.append("命中广告词（加微信/优惠券等）")
    if "short" in tags:
        parts.append("有效字数过短")
    if "template" in tags:
        parts.append(template or "模板好评")
    if "masked" in tags:
        parts.append("已脱敏手机号或地址")
    return "；".join(parts)


def normalize_key(platform: str, content: str) -> str:
    return "%s|%s" % (platform or "", CORE_RE.sub("", content or ""))


def clean_reviews(reviews: list[dict[str, Any]], aspects: list[dict[str, Any]] | None = None) -> dict[str, Any]:
    dicts = aspects or []
    prepared: list[dict[str, Any]] = []
    for row in reviews:
        original = str(row.get("content") or "")
        content, masked = mask_text(original)
        tags: list[str] = []
        extra_template = template_reason(content)
        if masked:
            tags.append("masked")
        if is_ad(content):
            tags.append("ad")
        if is_short(content):
            tags.append("short")
        if extra_template:
            tags.append("template")
        prepared.append(
            {
                "id": row.get("id"),
                "platform": row.get("platform") or "",
                "content": content,
                "tags": tags,
                "template": extra_template,
                "aspectHits": match_aspects(content, dicts),
            }
        )

    prepared.sort(key=lambda r: (r["id"] is None, r["id"] if isinstance(r["id"], int) else str(r["id"])))
    seen: set[str] = set()
    for row in prepared:
        key = normalize_key(row["platform"], row["content"])
        if key in seen:
            if "duplicate" not in row["tags"]:
                row["tags"].append("duplicate")
        else:
            seen.add(key)

    items = []
    stats = {
        "total": len(prepared),
        "kept": 0,
        "duplicate": 0,
        "ad": 0,
        "short": 0,
        "template": 0,
        "masked": 0,
    }
    for row in prepared:
        tags = row["tags"]
        counted = not any(tag in tags for tag in ("duplicate", "ad", "short", "template"))
        if counted:
            stats["kept"] += 1
        for tag in tags:
            if tag in stats:
                stats[tag] += 1
        items.append(
            {
                "id": row["id"],
                "content": row["content"],
                "tags": tags,
                "counted": counted,
                "reason": explain(tags, row.get("template")),
                "aspectHits": row["aspectHits"],
            }
        )

    message = "清洗完成：有效 %s/%s，去重 %s，广告 %s，过短 %s，模板 %s，脱敏 %s" % (
        stats["kept"],
        stats["total"],
        stats["duplicate"],
        stats["ad"],
        stats["short"],
        stats["template"],
        stats["masked"],
    )
    return {"items": items, "stats": stats, "message": message, "cleaned": stats["total"]}
