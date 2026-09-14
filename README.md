# 灯鉴 LumiInsight

AI 驱动的灯具全网评论采集与消费者洞察报告平台（参赛 / 产品仓）。

**当前状态（2026-09-14）：** 公开空仓 + 架构文档，**尚无业务代码**。

| 文档 | 说明 |
|------|------|
| [技术栈与架构说明书](docs/技术栈与架构说明书.md) | 给人看的完整架构（含多模型后台配置） |
| [三周实施计划](docs/三周实施计划.md) | 2026-09-14 起三周排期 |
| [AI 入口](docs/ai/AI_START_HERE.md) | Cursor / 其它 Agent 必读（D1–D6） |

## 仓库

- GitHub：`https://github.com/lkx960115/lumiinsight`（公开）
- 与公司 `ogsmProject` **独立**，不作为其子目录

## 本地以后怎么跑

业务代码落地后：Docker Compose 起 MySQL / Redis / Qdrant / MinIO → 启动 Java → 启动 Python Worker → 启动 Vue。步骤以说明书第 10 章为准。

## 安全

不要在 Issue、README、提交里粘贴 Key。模型密钥只放本机 `.env` 或后台加密配置。
