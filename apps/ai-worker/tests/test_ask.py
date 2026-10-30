import unittest

from app.ask import answer_question


class AskTest(unittest.TestCase):
    def test_empty_snippets_do_not_invent(self):
        result = answer_question("安装最常被吐槽的是什么？", [], None)
        self.assertEqual([], result["evidenceIds"])
        self.assertIn("还没有", result["answer"])
        self.assertEqual("empty", result["source"])

    def test_extractive_keeps_given_ids(self):
        result = answer_question(
            "安装怎么样",
            [{"id": 9, "content": "安装要打孔，师傅来了两次才装上", "platform": "jd", "sentiment": "neg"}],
            None,
        )
        self.assertEqual([9], result["evidenceIds"])
        self.assertEqual("rule", result["source"])
        self.assertIn("安装要打孔", result["answer"])

    def test_skips_rows_without_id(self):
        result = answer_question("色温", [{"content": "色温偏冷"}], None)
        self.assertEqual([], result["evidenceIds"])
        self.assertIn("还没有", result["answer"])
