# 灯鉴 LumiInsight

AI 驱动的灯具全网评论采集与消费者洞察报告平台（参赛 / 产品仓）。

**当前状态：** 第一周脚手架已入库（登录、权限、项目、评论导入、模型配置后台）。分析流水线在第二周。

| 文档 | 说明 |
|------|------|
| [技术栈与架构说明书](docs/技术栈与架构说明书.md) | 给人看的完整架构（含多模型后台配置） |
| [三周实施计划](docs/三周实施计划.md) | 2026-09-14 起三周排期 |
| [代码进度](docs/ai/CODE_PROGRESS.md) | 中断后续写从这里接 |
| [AI 入口](docs/ai/AI_START_HERE.md) | Cursor / 其它 Agent 必读（D1–D6） |

## 仓库

- GitHub：`https://github.com/lkx960115/lumiinsight`（公开）
- 与公司 `ogsmProject` **独立**，不作为其子目录

## 本地怎么跑

1. 复制 `.env.example` → `.env`（不要提交）。  
2. 根目录：`docker compose up -d`（MySQL / Redis / Qdrant / MinIO）。  
3. 启动后端：`mvn -f apps/server/pom.xml spring-boot:run`。  
   首次启动日志会打印本地管理员账号密码（只一次，不要写进 README）。  
4. 启动前端：`pnpm -C apps/web dev`，浏览器打开 http://localhost:5173 。  
5. 建项目后导入 `eval/fixtures/sample-reviews.csv`。  
6. 管理端配置 LLM Key 后再跑分析（分析在第二周）。

## 安全

不要在 Issue、README、提交里粘贴 Key 或初始密码。模型密钥只放本机 `.env` 或后台加密配置。
