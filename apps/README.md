# apps

| 目录 | 说明 |
|------|------|
| `server` | Spring Boot 3.3.5：登录、导入、模型配置、调 Worker |
| `web` | Vue3 工作台 `/app` 与管理端 `/admin` |
| `ai-worker` | FastAPI：清洗空实现，端口 8090 |

本地：`docker compose up -d`（MySQL 映射 3307）→ Java → Worker → `pnpm -C apps/web dev`。
