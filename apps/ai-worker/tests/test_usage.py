import unittest

from app.llm import add_usage, extract_usage


class UsageParseTest(unittest.TestCase):
    def test_openai_usage_fields(self):
        usage = extract_usage({"usage": {"prompt_tokens": 12, "completion_tokens": 8, "total_tokens": 20}})
        self.assertEqual({"promptTokens": 12, "completionTokens": 8, "totalTokens": 20}, usage)

    def test_missing_usage_is_zero(self):
        self.assertEqual({"promptTokens": 0, "completionTokens": 0, "totalTokens": 0}, extract_usage({}))

    def test_add_usage(self):
        total = add_usage(
            {"promptTokens": 1, "completionTokens": 2, "totalTokens": 3},
            {"promptTokens": 4, "completionTokens": 5, "totalTokens": 9},
        )
        self.assertEqual(5, total["promptTokens"])
        self.assertEqual(12, total["totalTokens"])


if __name__ == "__main__":
    unittest.main()
