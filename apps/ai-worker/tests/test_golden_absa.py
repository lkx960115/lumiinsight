import json
import unittest
from pathlib import Path

from app.absa import analyze_reviews


ROOT = Path(__file__).resolve().parents[3]
ASPECTS = json.loads((ROOT / "eval/fixtures/aspect-keywords.json").read_text(encoding="utf-8"))
GOLDEN = ROOT / "eval/fixtures/golden-absa.jsonl"


def load_golden():
    rows = []
    for line in GOLDEN.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(json.loads(line))
    return rows


def by_name(item):
    return {row["name"]: row for row in item.get("aspects") or []}


class GoldenAbsaTest(unittest.TestCase):
    def test_golden_aspect_sentiment(self):
        gold = load_golden()
        result = analyze_reviews(
            [{"id": row["id"], "content": row["content"]} for row in gold],
            ASPECTS,
            llm=None,
        )
        items = {item["id"]: item for item in result["items"]}
        missed = []
        for row in gold:
            got = by_name(items[row["id"]])
            for expect in row["aspects"]:
                name = expect["name"]
                want = expect["sentiment"]
                if name not in got:
                    missed.append("%s 缺少「%s」" % (row["id"], name))
                    continue
                if got[name]["sentiment"] != want:
                    missed.append(
                        "%s 「%s」应为 %s，实际 %s（%s）"
                        % (row["id"], name, want, got[name]["sentiment"], row["content"])
                    )
        self.assertFalse(missed, "\n".join(missed))


if __name__ == "__main__":
    unittest.main()
