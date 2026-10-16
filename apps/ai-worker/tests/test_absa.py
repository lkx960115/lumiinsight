import json
import unittest
from pathlib import Path

from app.absa import analyze_reviews, constrain_name, normalize_item

ASPECTS = json.loads(
    (Path(__file__).resolve().parents[3] / "eval/fixtures/aspect-keywords.json").read_text(encoding="utf-8")
)


def by_name(item):
    return {row["name"]: row for row in item["aspects"]}


class AbsaTest(unittest.TestCase):
    def test_constrain_unknown_to_other(self):
        self.assertEqual("其它", constrain_name("发光度", ["亮度", "其它"]))
        self.assertEqual("亮度", constrain_name("亮度", ["亮度", "其它"]))

    def test_llm_invented_aspect_becomes_other(self):
        names = ["亮度", "其它"]
        item = normalize_item(
            {
                "id": 1,
                "sentiment": "pos",
                "reason": "够亮",
                "confidence": 0.9,
                "aspects": [{"name": "发光度", "sentiment": "pos", "reason": "够亮", "confidence": 0.9}],
            },
            names,
            "llm",
        )
        self.assertEqual(["其它"], [row["name"] for row in item["aspects"]])

    def test_sample_texts_have_aspect_sentiment_reason(self):
        rows = [
            {"id": 1, "content": "亮度够用，客厅晚上很舒服"},
            {"id": 2, "content": "色温偏冷，希望有暖光档"},
            {"id": 3, "content": "物流有点慢，包装完好"},
            {"id": 4, "content": "没有明显频闪，护眼还行"},
        ]
        result = analyze_reviews(rows, ASPECTS)
        items = {item["id"]: item for item in result["items"]}
        self.assertEqual("pos", by_name(items[1])["亮度"]["sentiment"])
        self.assertTrue(by_name(items[1])["亮度"]["reason"])
        self.assertEqual("neg", by_name(items[2])["色温"]["sentiment"])
        self.assertEqual("neg", by_name(items[3])["物流"]["sentiment"])
        self.assertEqual("pos", by_name(items[3])["包装"]["sentiment"])
        self.assertEqual("pos", by_name(items[4])["频闪"]["sentiment"])
        self.assertGreaterEqual(items[1]["confidence"], 0.5)
        self.assertIn("词典规则", result["message"])

    def test_without_llm_does_not_call_network(self):
        result = analyze_reviews(
            [{"id": 1, "content": "售后回复及时"}],
            ASPECTS,
            llm=None,
        )
        item = result["items"][0]
        self.assertEqual("pos", by_name(item)["售后"]["sentiment"])
        self.assertEqual("rule", item["source"])

    def test_unique_reviews_collapses_duplicates(self):
        from app.absa import unique_reviews, copy_by_content

        rows = [
            {"id": 1, "content": "亮度够用"},
            {"id": 2, "content": "亮度够用"},
            {"id": 3, "content": "色温偏冷"},
        ]
        self.assertEqual(2, len(unique_reviews(rows)))
        copied = copy_by_content(
            {1: {"id": 1, "sentiment": "pos", "aspects": [{"name": "亮度"}]}},
            rows,
        )
        self.assertEqual("pos", copied[2]["sentiment"])
        self.assertEqual(2, copied[2]["id"])


if __name__ == "__main__":
    unittest.main()
