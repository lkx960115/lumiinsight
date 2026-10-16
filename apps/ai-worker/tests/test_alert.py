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
        self.assertEqual("rule", result["source"])
