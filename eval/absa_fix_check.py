"""D1/D2 修复对照脚本（离线、只读、不调模型）。

用法：
    python3 eval/absa_fix_check.py            # 打印对照结果
    python3 eval/absa_fix_check.py --json     # 输出结构化结果供 diff

对照三组：
  1) golden-absa.jsonl（31 条）：方面情感是否与人工标注一致
  2) attribution-diversity.csv（90 条，全为负向）：规则是否判出 neg
  3) 关键样例：针对「不够亮 / 偏暗 / 发暗 / 照不亮」等逐条展示判定
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "apps/ai-worker"))

from app.absa import rule_aspects  # noqa: E402
from app.clean import match_aspects  # noqa: E402

ASPECTS = json.loads((ROOT / "eval/fixtures/aspect-keywords.json").read_text(encoding="utf-8"))

# 关键样例：均为负向表达，期望规则判 neg
KEY_CASES = [
    ("客厅装上后明显不够亮，晚上看手机都费劲", "亮度"),
    ("亮度不够，餐桌上面那块还是暗的", "亮度"),
    ("用了半年感觉发暗，没有刚买时亮", "亮度"),
    ("卧室有点偏暗，看书还是不太行", "亮度"),
    ("灯光越来越暗，估计是光衰", "亮度"),
    ("这个瓦数根本照不亮客厅", "亮度"),
    ("晚上开着还是觉得昏暗", "亮度"),
    ("亮度够用，客厅晚上很舒服", "亮度"),
    ("色温偏冷，希望有暖光档", "色温"),
    ("安装比演示那款麻烦，说明书看不懂", "安装"),
]


def judge(content: str, aspect: str) -> tuple[str, str]:
    """返回 (是否命中方面, 情感)。未命中返回 miss。"""
    names = match_aspects(content, ASPECTS)
    if aspect not in names:
        return "miss", "-"
    for item in rule_aspects(content, ASPECTS):
        if item["name"] == aspect:
            return "hit", item["sentiment"]
    return "miss", "-"


def check_golden() -> dict:
    rows = []
    path = ROOT / "eval/fixtures/golden-absa.jsonl"
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(json.loads(line))
    missed = []
    for row in rows:
        got = {item["name"]: item["sentiment"] for item in rule_aspects(row["content"], ASPECTS)}
        for expect in row["aspects"]:
            name, want = expect["name"], expect["sentiment"]
            if name not in got:
                missed.append("%s 缺少「%s」" % (row["id"], name))
            elif got[name] != want:
                missed.append("%s 「%s」应为 %s 实际 %s（%s）" % (row["id"], name, want, got[name], row["content"]))
    return {"total": len(rows), "failed": len(missed), "detail": missed}


def check_diversity() -> dict:
    path = ROOT / "eval/fixtures/attribution-diversity.csv"
    rows = list(csv.DictReader(path.read_text(encoding="utf-8-sig").splitlines()))
    by_aspect: dict[str, list[bool]] = {}
    for row in rows:
        aspect = str(row["预期类别"]).split("-")[0].strip()
        names = match_aspects(row["原文"], ASPECTS)
        hit = aspect in names
        neg = False
        if hit:
            neg = any(
                item["name"] == aspect and item["sentiment"] == "neg"
                for item in rule_aspects(row["原文"], ASPECTS)
            )
        by_aspect.setdefault(aspect, []).append(neg)
    summary = {}
    for aspect, flags in sorted(by_aspect.items()):
        summary[aspect] = {
            "total": len(flags),
            "neg": sum(1 for f in flags if f),
            "recall": round(sum(1 for f in flags if f) / len(flags), 3),
        }
    return summary


def check_cases() -> list[dict]:
    out = []
    for content, aspect in KEY_CASES:
        hit, sent = judge(content, aspect)
        out.append({"原文": content, "方面": aspect, "命中": hit, "情感": sent})
    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    result = {"golden": check_golden(), "diversity": check_diversity(), "cases": check_cases()}
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return
    g = result["golden"]
    print("## 黄金集（%s 条）" % g["total"])
    print("判定不一致：%s 条" % g["failed"])
    for line in g["detail"]:
        print("  - " + line)
    print()
    print("## 多样性评测集（负向召回）")
    print("| 方面 | 条数 | 判 neg | 召回率 |")
    print("|---|---|---|---|")
    for aspect, item in result["diversity"].items():
        print("| %s | %s | %s | %.0f%% |" % (aspect, item["total"], item["neg"], item["recall"] * 100))
    print()
    print("## 关键样例")
    print("| 原文 | 方面 | 命中 | 情感 |")
    print("|---|---|---|---|")
    for case in result["cases"]:
        print("| %s | %s | %s | %s |" % (case["原文"], case["方面"], case["命中"], case["情感"]))


if __name__ == "__main__":
    main()
