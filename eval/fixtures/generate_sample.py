# 生成演示导入文件。不含真实手机号。

from pathlib import Path

ROOT = Path(__file__).resolve().parent
platforms = ["xiaohongshu", "jd", "taobao", "douyin"]
texts = [
    "亮度够用，客厅晚上很舒服",
    "色温偏冷，希望有暖光档",
    "外观简约，和墙面挺搭",
    "安装说明清楚，自己搞定了",
    "物流有点慢，包装完好",
    "价格略贵但做工扎实",
    "遥控偶尔失灵，重启就好",
    "没有明显频闪，护眼还行",
    "售后回复及时",
    "智能联动偶发掉线",
]

rows = ["平台,原文,时间,商品ID,评论ID,点赞,作者匿名ID,商品名,链接"]
for i in range(1, 121):
    p = platforms[i % 4]
    t = texts[i % len(texts)]
    rows.append(
        f"{p},{t},2026-03-{(i % 28) + 1:02d} 10:00:00,SKU-{i:04d},R{i:05d},{i % 20},anon{i:04d},演示吸顶灯,https://example.com/r/{i}"
    )

(ROOT / "sample-reviews.csv").write_text("\n".join(rows) + "\n", encoding="utf-8")
print("wrote", ROOT / "sample-reviews.csv", "rows", 120)
