# DOMAIN_LUMI — 灯鉴领域口径

权威业务词。D1/D4 只保留指针。

## 1. 项目

用户分析单元：品类、品牌、主型号、竞品列表、关键词、平台勾选、时间范围。MVP 平台勾选仅影响「期望数据范围」展示，不触发拉取。

## 2. 评论 Review

统一字段：platform、productId、reviewId、content、reviewTime、likeCount、authorHash、productName、sourceUrl、rawPayload。

## 3. Excel 列（MVP 模板）

| 列名 | 必填 | 说明 |
|------|------|------|
| 平台 | 是 | `xiaohongshu` / `jd` / `taobao` / `douyin` 或中文别名映射 |
| 原文 | 是 | 评论文本 |
| 时间 | 建议 | ISO 或 `yyyy-MM-dd HH:mm:ss` |
| 商品ID | 否 | |
| 评论ID | 否 | 无则内容哈希 |
| 点赞 | 否 | 数字 |
| 作者匿名ID | 否 | 导入后仍可再哈希 |
| 商品名 | 否 | |
| 链接 | 否 | 溯源外链 |

## 4. 方面

见 D4 种子词。四元组（目标）：实体 — 方面 — 情感(正/负/中) — 原因短句。

## 5. 报告章节（单品 MVP）

执行摘要、声量、情感结构、方面痛点/卖点、原声摘录、改进建议。每一观点块绑定 evidence。

## 6. 任务状态

`PENDING | IMPORTING | CLEANING | ANALYZING | INDEXING | REPORTING | READY | FAILED`
