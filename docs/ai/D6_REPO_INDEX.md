# D6 Repo 级理解 — lumiinsight

## 目录（2026-09-14，W1 代码已落地）

```
lumiinsight/
├── docker-compose.yml
├── pnpm-workspace.yaml
├── apps/server/          Spring Boot 3.3 / 启动类 LumiInsightApplication
├── apps/web/             Vue3 + Vite + Element Plus
├── apps/ai-worker/       清洗：/health、POST /v1/jobs/clean
├── eval/fixtures/        sample-reviews.csv（120 条）
└── docs/ai/CODE_PROGRESS.md
```

## 怎么找

| 问题 | 去哪 |
|------|------|
| 技术选型 / 多模型 | 说明书第 4、8 章；D1 |
| 能否爬虫 | D2、LOCAL_RULES；`ChannelAdapter` 空实现 |
| 三周做什么 | `docs/三周实施计划.md` + `docs/ai/CODE_PROGRESS.md` |
| 方面词 / Excel 列 | DOMAIN_LUMI；`eval/fixtures/sample-reviews.csv` |
| 源码入口 | 见下表 |

## 源码入口

| 能力 | 路径 |
|------|------|
| 启动类 | `apps/server/.../LumiInsightApplication.java` |
| 登录 JWT | `modules/auth` |
| RBAC / 首次管理员 | `modules/sys`（密码仅启动日志打印） |
| 项目 | `modules/project` |
| 导入 | `modules/importdata` |
| 评论列表 | `modules/review` |
| 模型配置 | `modules/llm` |
| 流水线 / Worker 客户端 | `modules/pipeline`；Worker：`apps/ai-worker/app/main.py` |
| 四平台空适配器 | `modules/channel` |
| 健康检查 | `GET /api/v1/health`、`/actuator/health` |
| 前端路由 | `apps/web/src/router/index.ts`：`/login` `/app/*` `/admin/*` |
| 权限指令 | `apps/web/src/directives/permission.ts`（与库表 `sys_permission.code` 同名） |

## 核心表

`sys_user` `sys_role` `sys_permission` `sys_user_role` `sys_role_permission`  
`project` `project_member` `import_job` `review` `pipeline_job`  
`llm_provider` `llm_model` `llm_route` `aspect_dict` `audit_log`

Worker 路由：`GET /health`、`POST /v1/jobs/clean`（去重 / 广告 / 过短 / 脱敏）。
