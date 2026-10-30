"""归因离线探针（只读样例/评测集，不改业务代码）。

用途：验证「方案 B 的聚簇一半」是否可用——在不调模型的前提下，
用真实方面词典 + 真实规则情感（apps/ai-worker/app/absa.py）筛出负向评论，
再用字符 bigram 相似度聚类，看分出来的簇是否可读、是否与人工预期一致。

用法：
    python3 eval/offline_attribution_probe.py
    python3 eval/offline_attribution_probe.py --csv eval/fixtures/attribution-diversity.csv --truth 预期类别 --sweep

参数：
    --csv    评测集路径（默认 eval/fixtures/sample-reviews.csv）
    --truth  预期类别列名（有则计算纯度与碎片度）
    --sweep  扫描多个阈值并输出对比
    --min    最小负向条数（默认 10）

本脚本不写库、不调模型、不改动 apps/ 下任何文件。
"""

from __future__ import annotations

import csv
import math
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "apps", "ai-worker"))

from app.absa import rule_aspects  # noqa: E402  真实规则引擎，保证口径与线上一致

SAMPLE = os.path.join(ROOT, "eval", "fixtures", "sample-reviews.csv")
MIGRATIONS = os.path.join(ROOT, "apps", "server", "src", "main", "resources", "db", "migration")
SQL_FILES = (
    "V4__aspect_dict_keywords.sql",
    "V11__aspect_keywords_golden.sql",
    "V12__aspect_keywords_bright_price.sql",
)
SWEEP = (0.20, 0.25, 0.30, 0.34, 0.40, 0.50)

STOP = set(
    "的了很也就还有都太有点感觉说买装一个这东西时后里上去下到把被我你会很真挺还蛮比较非常特别"
    "但是不过所以而且然后现在就是没什么可以这个那个这样那样怎么知道觉得还是已经真的确实"
)

KW_RE = re.compile(r"SET keywords = '(.*?)' WHERE name = '([^']+)'")


def load_dict() -> list[dict]:
    mapping: dict[str, str] = {}
    for name in SQL_FILES:
        path = os.path.join(MIGRATIONS, name)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as fh:
            for kw, asp in KW_RE.findall(fh.read()):
                if kw:
                    mapping[asp] = kw
    return [{"name": k, "keywords": v} for k, v in mapping.items()]


def load_reviews(path: str, truth_col: str | None) -> list[dict]:
    rows = []
    with open(path, encoding="utf-8-sig") as fh:
        for idx, row in enumerate(csv.DictReader(fh), start=1):
            content = (row.get("原文") or "").strip()
            if not content:
                continue
            rows.append(
                {
                    "id": idx,
                    "content": content,
                    "platform": (row.get("平台") or "").strip(),
                    "time": (row.get("时间") or "").strip()[:10],
                    "truth": (row.get(truth_col) or "").strip() if truth_col else "",
                }
            )
    return rows


def bigrams(text: str) -> list[str]:
    cleaned = re.sub(r"[^\u4e00-\u9fff0-9a-zA-Z]+", "", text)
    return [cleaned[i : i + 2] for i in range(len(cleaned) - 1)] or [cleaned]


def vector(text: str) -> dict[str, float]:
    raw: dict[str, float] = {}
    for gram in bigrams(text):
        raw[gram] = raw.get(gram, 0.0) + 1.0
    for gram in raw:
        raw[gram] = 1.0 + math.log(raw[gram])
    norm = math.sqrt(sum(v * v for v in raw.values())) or 1.0
    return {k: v / norm for k, v in raw.items()}


def cosine(a: dict[str, float], b: dict[str, float]) -> float:
    if len(a) > len(b):
        a, b = b, a
    return sum(a[k] * b.get(k, 0.0) for k in a)


def cluster(items: list[dict], threshold: float) -> list[list[dict]]:
    clusters: list[list[dict]] = []
    centroids: list[dict[str, float]] = []
    for item in items:
        vec = item["_vec"]
        best, best_score = -1, 0.0
        for idx, centroid in enumerate(centroids):
            score = cosine(vec, centroid)
            if score > best_score:
                best, best_score = idx, score
        if best >= 0 and best_score >= threshold:
            clusters[best].append(item)
            merged: dict[str, float] = {}
            for member in clusters[best]:
                for key, value in member["_vec"].items():
                    merged[key] = merged.get(key, 0.0) + value
            norm = math.sqrt(sum(v * v for v in merged.values())) or 1.0
            centroids[best] = {k: v / norm for k, v in merged.items()}
        else:
            clusters.append([item])
            centroids.append(dict(vec))
    clusters.sort(key=lambda c: -len(c))
    return clusters


def label(cluster_items: list[dict], aspect: str) -> str:
    counter: dict[str, int] = {}
    for item in cluster_items:
        for gram in bigrams(item["content"]):
            if any(ch in STOP for ch in gram):
                continue
            counter[gram] = counter.get(gram, 0) + 1
    top = sorted(counter.items(), key=lambda kv: -kv[1])[:3]
    words = "·".join(gram for gram, _ in top)
    return "%s｜%s" % (aspect, words) if words else aspect


def score(clusters: list[list[dict]]) -> tuple[float, float]:
    correct = 0
    total = 0
    spread: dict[str, int] = {}
    for group in clusters:
        counter: dict[str, int] = {}
        for item in group:
            key = item.get("truth") or "?"
            counter[key] = counter.get(key, 0) + 1
        if not counter:
            continue
        correct += max(counter.values())
        total += len(group)
        for key in counter:
            spread[key] = spread.get(key, 0) + 1
    purity = correct / total if total else 0.0
    avg_spread = sum(spread.values()) / len(spread) if spread else 0.0
    return purity, avg_spread


def main() -> None:
    args = sys.argv[1:]
    csv_path = SAMPLE
    truth_col = None
    sweep = "--sweep" in args
    min_neg = 10
    for idx, arg in enumerate(args):
        if arg == "--csv" and idx + 1 < len(args):
            csv_path = args[idx + 1]
        elif arg == "--truth" and idx + 1 < len(args):
            truth_col = args[idx + 1]
        elif arg == "--min" and idx + 1 < len(args):
            min_neg = int(args[idx + 1])
    if not os.path.isabs(csv_path):
        csv_path = os.path.join(ROOT, csv_path)

    aspects = load_dict()
    reviews = load_reviews(csv_path, truth_col)
    buckets: dict[str, list[dict]] = {}
    for row in reviews:
        for hit in rule_aspects(row["content"], aspects):
            if hit.get("sentiment") != "neg":
                continue
            buckets.setdefault(hit["name"], []).append(dict(row, reason=hit.get("reason") or ""))

    print("# 归因离线验证（%s，共 %s 条）" % (os.path.basename(csv_path), len(reviews)))
    print()
    ranked = sorted(buckets.items(), key=lambda kv: -len(kv[1]))
    print("| 方面 | 负向条数 | 独特表述数 | 独特率 | 够样本（>=%s） |" % min_neg)
    print("|---|---|---|---|---|")
    for name, items in ranked:
        unique = len({item["content"] for item in items})
        rate = "%.0f%%" % (100.0 * unique / len(items)) if items else "-"
        print("| %s | %s | %s | %s | %s |" % (name, len(items), unique, rate, "是" if len(items) >= min_neg else "否"))
    print()

    targets = [(n, i) for n, i in ranked if len(i) >= min_neg]
    best: dict[str, tuple[float, float]] = {}

    if sweep and truth_col:
        print("## 阈值扫描（纯度越高越好，碎片度越低越好）")
        print()
        print("| 阈值 | 方面 | 真实类别数 | 簇数 | 纯度 | 平均碎片度 |")
        print("|---|---|---|---|---|---|")
        for threshold in SWEEP:
            for name, items in targets:
                for item in items:
                    item["_vec"] = vector(item["content"])
                clusters = cluster(items, threshold)
                truth_count = len({i["truth"] for i in items if i["truth"]})
                purity, avg_spread = score(clusters)
                print(
                    "| %s | %s | %s | %s | %.0f%% | %.1f |"
                    % (threshold, name, truth_count, len(clusters), purity * 100, avg_spread)
                )
                if name not in best or purity > best[name][0]:
                    best[name] = (purity, threshold)
        print()
        print("各方面最佳阈值：")
        for name, (purity, threshold) in best.items():
            print("- %s：阈值 %s，纯度 %.0f%%" % (name, threshold, purity * 100))
        print()

    for name, items in targets:
        threshold = best.get(name, (0.0, 0.34))[1] if (sweep and truth_col) else 0.34
        for item in items:
            item["_vec"] = vector(item["content"])
        clusters = cluster(items, threshold)
        head = "## %s（负向 %s 条 → %s 簇，阈值 %s）" % (name, len(items), len(clusters), threshold)
        if truth_col:
            purity, avg_spread = score(clusters)
            head += "｜纯度 %.0f%%｜平均碎片度 %.1f" % (purity * 100, avg_spread)
        print(head)
        print()
        for idx, group in enumerate(clusters[:6], start=1):
            counter: dict[str, int] = {}
            for item in group:
                key = item.get("truth") or "-"
                counter[key] = counter.get(key, 0) + 1
            mix = "、".join("%s×%s" % (k, v) for k, v in sorted(counter.items(), key=lambda kv: -kv[1]))
            print("**类 %s｜%s**（%s 条｜预期构成：%s）" % (idx, label(group, name), len(group), mix))
            for row in group[:3]:
                print("- [%s] %s" % (row["id"], row["content"]))
            print()
        if len(clusters) > 6:
            print("（另有 %s 个小簇未展示）" % (len(clusters) - 6))
            print()


if __name__ == "__main__":
    main()
