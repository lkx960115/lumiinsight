# D6 Repo 级理解 — lumiinsight

## 目录（2026-09-14，W1 代码已落地）

```
lumiinsight/
├── docker-compose.yml
├── pnpm-workspace.yaml
├── apps/server/          Spring Boot 3.3 / 启动类 LumiInsightApplication
├── apps/web/             Vue3 + Vite + Element Plus
├── apps/ai-worker/       清洗/分析/问原评/预警：/health、POST /v1/jobs/clean、analyze、embed、ask、alert
├── eval/fixtures/        sample-reviews.csv（120 条）
├── docs/演示说明.md
└── docs/ai/CODE_PROGRESS.md
```

## 怎么找

| 问题 | 去哪 |
|------|------|
| 技术选型 / 多模型 | 说明书第 4 章（含为何选 Qdrant 等）、第 8 章；D1 只锁表 |
| 能否爬虫 | D2、LOCAL_RULES；`ChannelAdapter` 空实现 |
| 工作台长什么样 | [PRODUCT_UI.md](PRODUCT_UI.md)（按钮、列表、弹窗正本） |
| 排期与路线 | `docs/产品路线图.md` + `docs/ai/CODE_PROGRESS.md` |
| 方面词 / Excel 列 | DOMAIN_LUMI；`eval/fixtures/sample-reviews.csv` |
| 15 分钟演示 | `docs/演示说明.md` |
| 源码入口 | 见下表 |

## 源码入口

| 能力 | 路径 |
|------|------|
| 启动类 | `apps/server/.../LumiInsightApplication.java` |
| 登录 JWT | `modules/auth` |
| RBAC / 首次管理员 | `modules/sys`（密码仅启动日志打印） |
| 项目 | `modules/project` |
| 导入 | `modules/importdata`（认灯鉴模板与商家后台导出列名） |
| 评论列表 | `modules/review` |
| 模型配置 | `modules/llm` |
| 用量 | 前端 `/admin/usage`（`UsageView.vue`），接口仍走模型配置权限 |
| 报告 | `modules/report`；前端 `/app/reports`；导出表格 / 文稿 / PDF |
| 问原评 | `modules/ask`；Worker `POST /v1/jobs/ask`；前端 `AskDock.vue`（仅项目详情/报告） |
| 口碑预警 | `modules/alert`；Worker `POST /v1/jobs/alert`；项目详情「口碑预警」（负向变多 / 新槽点 / 声量骤降） |
| 流水线 / Worker 客户端 | `modules/pipeline`；Worker：`apps/ai-worker/app/main.py` |
| 四平台空适配器 | `modules/channel` |
| 健康检查 | `GET /api/v1/health`、`/actuator/health` |
| 前端路由 | `apps/web/src/router/index.ts`：`/login` `/app/*` `/admin/*` |
| 权限指令 | `apps/web/src/directives/permission.ts`（与库表 `sys_permission.code` 同名） |

## 核心表

`sys_user` `sys_role` `sys_permission` `sys_user_role` `sys_role_permission`  
`project` `project_member` `import_job` `review` `review_aspect` `pipeline_job`  
`llm_provider` `llm_model` `llm_route` `llm_usage` `aspect_dict` `audit_log` `evidence` `report`

Worker 路由：`GET /health`、`POST /v1/jobs/clean`（去重 / 广告 / 过短 / 脱敏）、`POST /v1/jobs/analyze`（情感 + ABSA，方面名必须落在词典或「其它」）、`POST /v1/jobs/ask`（只根据给定原评作答）、`POST /v1/jobs/alert`（预警说明）。
