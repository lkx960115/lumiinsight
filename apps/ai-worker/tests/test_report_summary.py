import unittest

from app.report_summary import rewrite_sections


class ReportSummaryTest(unittest.TestCase):
    def test_empty_payload(self):
        result = rewrite_sections(None, None)
        self.assertEqual([], result["sections"])
        self.assertEqual("empty", result["source"])

    def test_no_model_keeps_draft_and_ids(self):
        result = rewrite_sections(
            {
                "sections": [
                    {
                        "code": "summary",
                        "claims": [{"text": "安装负向集中。", "evidenceIds": [3, 4]}],
                    },
                    {
                        "code": "advice",
                        "claims": [{"text": "优先改安装。", "evidenceIds": [3]}],
                    },
                ],
                "snippets": [
                    {"id": 3, "quote": "安装要打孔", "aspect": "安装"},
                    {"id": 4, "quote": "说明书看不懂", "aspect": "安装"},
                ],
            },
            None,
        )
        self.assertEqual("rule", result["source"])
        codes = [row["code"] for row in result["sections"]]
        self.assertEqual(["summary", "advice"], codes)
        self.assertEqual([3, 4], result["sections"][0]["claims"][0]["evidenceIds"])
        self.assertIn("安装负向集中", result["sections"][0]["claims"][0]["text"])

    def test_skips_other_sections_and_claims_without_ids(self):
        result = rewrite_sections(
            {
                "sections": [
                    {"code": "ask", "claims": [{"text": "问：安装？", "evidenceIds": [1]}]},
                    {"code": "summary", "claims": [{"text": "空话", "evidenceIds": []}]},
                    {"code": "summary", "claims": [{"text": "有依据", "evidenceIds": [9]}]},
                ],
                "snippets": [{"id": 9, "quote": "色温偏冷"}],
            },
            None,
        )
        self.assertEqual(["summary"], [row["code"] for row in result["sections"]])
        self.assertEqual([9], result["sections"][0]["claims"][0]["evidenceIds"])
        self.assertEqual("有依据", result["sections"][0]["claims"][0]["text"])
