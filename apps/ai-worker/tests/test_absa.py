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
        flicker = by_name(items[4])["频闪"]["reason"]
        self.assertNotEqual("否定问题表述", flicker)
        self.assertIn("频闪", flicker)
        self.assertGreaterEqual(items[1]["confidence"], 0.5)
        self.assertIn("词典规则", result["message"])

    def test_negated_flicker_uses_review_words(self):
        from app.absa import window_polarity

        sentiment, reason, _ = window_polarity("没有明显频闪，护眼还行", "频闪")
        self.assertEqual("pos", sentiment)
        self.assertNotEqual("否定问题表述", reason)
        self.assertIn("频闪", reason)

    def test_without_llm_does_not_call_network(self):
        result = analyze_reviews(
            [{"id": 1, "content": "售后回复及时"}],
            ASPECTS,
            llm=None,
        )
        item = result["items"][0]
        self.assertEqual("pos", by_name(item)["售后"]["sentiment"])
        self.assertEqual("rule", item["source"])

    def test_negated_positive_word_is_not_positive(self):
        """D1：「不够亮」曾被 NEGATORS 漏掉而判为正向。"""
        rows = [{"id": 1, "content": "客厅装上后明显不够亮，晚上看手机都费劲"}]
        result = analyze_reviews(rows, ASPECTS)
        self.assertEqual("neg", by_name(result["items"][0])["亮度"]["sentiment"])

    def test_short_negative_form_is_negative(self):
        """D1 延伸：「亮度不够」这类省略式也应判负向。"""
        rows = [{"id": 1, "content": "亮度不够，餐桌上面那块还是暗的"}]
        result = analyze_reviews(rows, ASPECTS)
        self.assertEqual("neg", by_name(result["items"][0])["亮度"]["sentiment"])

    def test_darkness_words_hit_brightness_as_negative(self):
        """D2：暗光类表述应命中亮度并判负向。"""
        texts = [
            "卧室有点偏暗，看书还是不太行",
            "用了半年感觉发暗，没有刚买时亮",
            "这个瓦数根本照不亮客厅",
            "晚上开着还是觉得昏暗",
            "灯光越来越暗，估计是光衰",
        ]
        for text in texts:
            with self.subTest(text=text):
                result = analyze_reviews([{"id": 1, "content": text}], ASPECTS)
                got = by_name(result["items"][0])
                self.assertIn("亮度", got)
                self.assertEqual("neg", got["亮度"]["sentiment"])

    def test_positive_brightness_not_broken_by_fix(self):
        """修复不得误伤正向判定。"""
        rows = [{"id": 1, "content": "亮度够用，客厅晚上很舒服"}]
        result = analyze_reviews(rows, ASPECTS)
        self.assertEqual("pos", by_name(result["items"][0])["亮度"]["sentiment"])

    def test_negated_negative_word_is_not_negative(self):
        """否定词紧贴负向词时不得判负向（「不刺眼」是正面表述）。"""
        from app.absa import window_polarity

        sentiment, _, _ = window_polarity("客厅亮度可以，不刺眼", "刺眼")
        self.assertNotEqual("neg", sentiment)

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
