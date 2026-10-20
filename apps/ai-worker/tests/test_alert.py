import unittest

from app.alert import write_alerts


class AlertTest(unittest.TestCase):
    def test_empty_items(self):
        result = write_alerts([], None)
        self.assertEqual([], result["items"])

    def test_rule_copy_keeps_quote_and_ids(self):
        result = write_alerts(
            [
                {
                    "aspect": "安装",
                    "recentLabel": "2026-04",
                    "previousLabel": "2026-03",
                    "recentNeg": 3,
                    "previousNeg": 0,
                    "snippets": [{"id": 8, "content": "安装说明书还是看不懂"}],
                }
            ],
            None,
        )
        item = result["items"][0]
        self.assertEqual("安装", item["aspect"])
        self.assertEqual([8], item["evidenceIds"])
        self.assertIn("安装说明书还是看不懂", item["summary"])
        self.assertIn("2026-04", item["summary"])
        self.assertEqual("neg_spike", item["kind"])
        self.assertEqual("rule", result["source"])

    def test_new_pain_copy(self):
        result = write_alerts(
            [
                {
                    "kind": "new_pain",
                    "aspect": "物流",
                    "recentLabel": "2026年4月",
                    "previousLabel": "2026年3月",
                    "recentNeg": 2,
                    "previousNeg": 0,
                    "recentVolume": 2,
                    "previousVolume": 0,
                    "snippets": [{"id": 9, "content": "物流破损，外箱都凹了"}],
                }
            ],
            None,
        )
        item = result["items"][0]
        self.assertEqual("new_pain", item["kind"])
        self.assertIn("新出现", item["summary"])
        self.assertIn("物流破损", item["summary"])
        self.assertEqual([9], item["evidenceIds"])

    def test_volume_drop_copy(self):
        result = write_alerts(
            [
                {
                    "kind": "volume_drop",
                    "aspect": "声量",
                    "recentLabel": "2026年4月",
                    "previousLabel": "2026年3月",
                    "recentVolume": 14,
                    "previousVolume": 120,
                    "snippets": [{"id": 3, "content": "安装说明清楚，自己搞定了"}],
                }
            ],
            None,
        )
        item = result["items"][0]
        self.assertEqual("volume_drop", item["kind"])
        self.assertIn("14", item["summary"])
        self.assertIn("120", item["summary"])
        self.assertIn("声量", item["summary"])
        self.assertIn("安装说明清楚", item["summary"])
        self.assertEqual([3], item["evidenceIds"])
