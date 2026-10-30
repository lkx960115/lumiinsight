# 生成演示导入文件。不含真实手机号。
# 一次导入要能看到：问原评章节、口碑预警（负向变多/新槽点/声量骤降）、竞品对照。

from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
WEB = REPO / "apps" / "web" / "public"
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
own_april = [
    ("jd", "色温更冷了，晚上刺眼"),
    ("taobao", "安装说明书还是看不懂"),
    ("douyin", "色温偏冷，没有暖光档"),
    ("xiaohongshu", "自己安装对不上孔位"),
    ("jd", "色温忽冷忽暖，希望能调"),
    ("taobao", "安装要找师傅，说明书不行"),
    ("jd", "售后推诿，换货等了一周"),
    ("taobao", "售后推诿，换货等了一周"),
    ("douyin", "售后推诿，换货等了一周"),
    ("xiaohongshu", "价格太贵，不值这个钱"),
    ("jd", "物流破损，外箱都凹了"),
    ("taobao", "卫生间防水差，漏光"),
    ("douyin", "浴室防水差，漏光"),
]
competitors = [
    ("jd", "安装比演示那款麻烦，说明书看不懂"),
    ("taobao", "色温偏黄，客厅显得闷"),
    ("douyin", "价格便宜但频闪明显"),
    ("xiaohongshu", "外观更花哨，和墙面不太搭"),
    ("jd", "售后推诿，换货等了一周"),
    ("taobao", "亮度够，就是遥控器难配对"),
    ("douyin", "包装简陋，灯罩有划痕"),
    ("xiaohongshu", "智能联动经常掉线，不如演示那款稳"),
]

rows = ["平台,原文,时间,商品ID,评论ID,点赞,作者匿名ID,商品名,链接"]
for i in range(1, 121):
    p = platforms[i % 4]
    t = texts[i % len(texts)]
    rows.append(
        f"{p},{t},2026-03-{(i % 28) + 1:02d} 10:00:00,SKU-{i:04d},R{i:05d},{i % 20},anon{i:04d},演示吸顶灯,https://example.com/r/{i}"
    )
for i, (p, t) in enumerate(own_april, start=1):
    rows.append(
        f"{p},{t},2026-04-{i + 1:02d} 10:00:00,SKU-A{i:02d},RA{i:03d},{i},{f'anonA{i:02d}'},演示吸顶灯,https://example.com/r/a{i}"
    )
for i, (p, t) in enumerate(competitors, start=1):
    rows.append(
        f"{p},{t},2026-04-{i + 1:02d} 10:00:00,SKU-C{i:02d},RC{i:03d},{i + 2},{f'anonC{i:02d}'},星辉竞品灯,https://example.com/r/c{i}"
    )

text = "\n".join(rows) + "\n"
compete_rows = [rows[0]] + [line for line in rows[1:] if "星辉竞品灯" in line]
compete_text = "\n".join(compete_rows) + "\n"
targets = [ROOT, WEB]
for folder in targets:
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "sample-reviews.csv").write_text(text, encoding="utf-8-sig")
    (folder / "sample-competitor-reviews.csv").write_text(compete_text, encoding="utf-8-sig")
print("wrote", len(rows) - 1, "rows to fixtures and web/public")
