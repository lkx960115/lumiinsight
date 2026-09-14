# apps

| 目录 | 说明 |
|------|------|
| `server` | Spring Boot 3 / JDK17 字节码：登录、RBAC、项目、导入、模型配置 |
| `web` | Vue3 + Element Plus：`/login` `/app` `/admin` |
| `ai-worker` | W2 再写 FastAPI |

本地：根目录 `docker compose up -d` → 启动 server → `pnpm -C apps/web dev`。
