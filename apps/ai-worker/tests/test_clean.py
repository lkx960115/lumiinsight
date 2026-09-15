import unittest

from app.clean import clean_reviews


class CleanRulesTest(unittest.TestCase):
    def test_dedup_same_platform(self):
        result = clean_reviews(
            [
                {"id": 1, "platform": "jd", "content": "外观简约，和墙面挺搭"},
                {"id": 2, "platform": "jd", "content": "外观简约，和墙面挺搭"},
                {"id": 3, "platform": "taobao", "content": "外观简约，和墙面挺搭"},
            ]
        )
        by_id = {item["id"]: item for item in result["items"]}
        self.assertTrue(by_id[1]["counted"])
        self.assertFalse(by_id[2]["counted"])
        self.assertIn("duplicate", by_id[2]["tags"])
        self.assertTrue(by_id[3]["counted"])
        self.assertEqual(2, result["stats"]["kept"])
        self.assertEqual(1, result["stats"]["duplicate"])

    def test_mask_phone_not_echoed(self):
        result = clean_reviews(
            [{"id": 1, "platform": "jd", "content": "安装师傅联系我 13800138000 就可以"}]
        )
        item = result["items"][0]
        self.assertNotIn("13800138000", item["content"])
        self.assertIn("1**********", item["content"])
        self.assertIn("masked", item["tags"])
        self.assertTrue(item["counted"])

    def test_template_and_aspect_dict(self):
        aspects = [{"name": "色温", "keywords": "色温,暖光,冷光"}]
        result = clean_reviews(
            [
                {"id": 1, "platform": "jd", "content": "好评"},
                {"id": 2, "platform": "jd", "content": "色温偏冷，希望有暖光档"},
            ],
            aspects,
        )
        by_id = {item["id"]: item for item in result["items"]}
        self.assertIn("template", by_id[1]["tags"])
        self.assertFalse(by_id[1]["counted"])
        self.assertIn("模板好评", by_id[1]["reason"])
        self.assertEqual(["色温"], by_id[2]["aspectHits"])
        self.assertTrue(by_id[2]["counted"])

    def test_dict_change_changes_hits(self):
        row = [{"id": 1, "platform": "jd", "content": "客厅晚上很舒服"}]
        before = clean_reviews(row, [{"name": "色温", "keywords": "色温"}])
        after = clean_reviews(row, [{"name": "亮度", "keywords": "够用,舒服,亮度"}])
        self.assertEqual([], before["items"][0]["aspectHits"])
        self.assertEqual(["亮度"], after["items"][0]["aspectHits"])

    def test_ad_and_short(self):
        result = clean_reviews(
            [
                {"id": 1, "platform": "jd", "content": "加微信领取优惠券同款加v"},
                {"id": 2, "platform": "jd", "content": "好!!"},
            ]
        )
        by_id = {item["id"]: item for item in result["items"]}
        self.assertIn("ad", by_id[1]["tags"])
        self.assertFalse(by_id[1]["counted"])
        self.assertIn("short", by_id[2]["tags"])
        self.assertFalse(by_id[2]["counted"])
        self.assertEqual(0, result["stats"]["kept"])


if __name__ == "__main__":
    unittest.main()
