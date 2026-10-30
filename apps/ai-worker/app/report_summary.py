"""报告摘要：只改写已有结论和原评，失败回退草稿。"""

from __future__ import annotations

from typing import Any

from app.llm import chat_json

REWRITE_RULES = """你是灯鉴口碑顾问。下面是已经根据原评写好的报告章节草稿，请改写成更像顾问结论的中文。
规则：
1. 只能改写已给的事实和原评，不许补充竞品销量、未出现的型号或行业常识。
2. 章节 code 只能用 summary 或 advice。条数不必与草稿相同，但每条必须带 evidenceIds，且只能用该章给出的 id。
3. 先给结论，再点到原评。不要寒暄。
4. 只输出 JSON 对象：{"sections":[{"code":"summary","claims":[{"text":"中文","evidenceIds":[id,...]}]},{"code":"advice","claims":[...]}]}，不要 Markdown。
"""

ALLOWED_CODES = ("summary", "advice")


def rewrite_sections(payload: dict[str, Any] | None, spec: dict[str, Any] | None) -> dict[str, Any]:
    sections, allowed, prompt_body = prepare(payload)
    if not sections:
        return {"sections": [], "source": "empty", "usage": {}}
    drafts = [pack_section(row) for row in sections]
    if not spec or not spec.get("apiKey"):
        return {"sections": drafts, "source": "rule", "usage": {}}
    prompt = REWRITE_RULES + "\n" + prompt_body
    try:
        data, usage = chat_json(spec, prompt)
    except Exception:
        return {"sections": drafts, "source": "rule", "usage": {}}
    rewritten = parse_sections(data, allowed)
    if not rewritten:
        return {"sections": drafts, "source": "rule", "usage": usage}
    merged = []
    by_code = {row["code"]: row for row in rewritten}
    for draft in drafts:
        picked = by_code.get(draft["code"])
        if picked and picked.get("claims"):
            merged.append(picked)
        else:
            merged.append(draft)
    source = "llm" if any(row.get("source") == "llm" for row in merged) else "rule"
    return {"sections": merged, "source": source, "usage": usage}


def prepare(payload: dict[str, Any] | None) -> tuple[list[dict[str, Any]], dict[str, set[int]], str]:
    raw_sections = (payload or {}).get("sections") if isinstance(payload, dict) else None
    snippets = (payload or {}).get("snippets") if isinstance(payload, dict) else None
    snippet_map, global_ids = snippet_index(snippets)
    sections: list[dict[str, Any]] = []
    allowed: dict[str, set[int]] = {}
    blocks: list[str] = []
    if not isinstance(raw_sections, list):
        return sections, allowed, ""
    for row in raw_sections:
        if not isinstance(row, dict):
            continue
        code = str(row.get("code") or "").strip()
        if code not in ALLOWED_CODES:
            continue
        ids: set[int] = set()
        claims = []
        for claim in row.get("claims") or []:
            if not isinstance(claim, dict):
                continue
            text = str(claim.get("text") or "").strip()
            claim_ids = filter_ids(claim.get("evidenceIds"), global_ids or None)
            if not text or not claim_ids:
                continue
            ids.update(claim_ids)
            claims.append({"text": clip(text, 500), "evidenceIds": claim_ids[:3]})
        if not claims:
            continue
        allowed[code] = ids
        sections.append({"code": code, "claims": claims, "ids": ids})
        quote_lines = []
        for eid in list(ids)[:8]:
            quote_lines.append(snippet_map.get(eid) or "[%s]" % eid)
        claim_lines = ["- %s 依据 %s" % (item["text"], item["evidenceIds"]) for item in claims]
        blocks.append(
            "章节：%s\n可用 id：%s\n草稿：\n%s\n原评：\n%s"
            % (code, sorted(ids), "\n".join(claim_lines), "\n".join(quote_lines) or "（无）")
        )
    return sections, allowed, "\n\n".join(blocks)


def parse_sections(data: Any, allowed: dict[str, set[int]]) -> list[dict[str, Any]]:
    raw = data.get("sections") if isinstance(data, dict) else None
    if not isinstance(raw, list):
        return []
    out: list[dict[str, Any]] = []
    seen = set()
    for row in raw:
        if not isinstance(row, dict):
            continue
        code = str(row.get("code") or "").strip()
        if code not in ALLOWED_CODES or code in seen:
            continue
        ids = allowed.get(code) or set()
        claims = []
        for claim in row.get("claims") or []:
            if not isinstance(claim, dict):
                continue
            text = str(claim.get("text") or "").strip()
            evidence_ids = filter_ids(claim.get("evidenceIds"), ids)
            if not text or not evidence_ids:
                continue
            claims.append({"text": clip(text, 500), "evidenceIds": evidence_ids[:3], "source": "llm"})
        if not claims:
            continue
        seen.add(code)
        out.append({"code": code, "claims": claims, "source": "llm"})
    return out


def pack_section(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "code": row["code"],
        "claims": [
            {"text": item["text"], "evidenceIds": item["evidenceIds"], "source": "rule"}
            for item in row["claims"]
        ],
        "source": "rule",
    }


def snippet_index(snippets: Any) -> tuple[dict[int, str], set[int]]:
    mapping: dict[int, str] = {}
    ids: set[int] = set()
    if not isinstance(snippets, list):
        return mapping, ids
    for row in snippets:
        if not isinstance(row, dict) or row.get("id") is None:
            continue
        try:
            rid = int(row.get("id"))
        except (TypeError, ValueError):
            continue
        ids.add(rid)
        content = str(row.get("quote") or row.get("content") or "").strip().replace("\n", " ")
        if len(content) > 180:
            content = content[:180] + "…"
        aspect = str(row.get("aspect") or "").strip()
        prefix = "「%s」" % aspect if aspect else ""
        mapping[rid] = "[%s] %s%s" % (rid, prefix, content)
    return mapping, ids


def filter_ids(raw: Any, allowed: set[int] | None) -> list[int]:
    ids: list[int] = []
    if not isinstance(raw, list):
        return ids
    for item in raw:
        try:
            value = int(item)
        except (TypeError, ValueError):
            continue
        if allowed is not None and value not in allowed:
            continue
        if value not in ids:
            ids.append(value)
    return ids


def clip(text: str, limit: int) -> str:
    if len(text) <= limit:
        return text
    return text[:limit] + "…"
