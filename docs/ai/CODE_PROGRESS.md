# 代码编写进度（中断后从这里接）

> 给人或 Agent：先读本文件，再动手。不要重做「已完成」项。  
> 用户已于 2026-09-14 确认：**列出计划并按计划执行**（闸门已开）。

## 怎么接上

1. 读本文件「当前指针」与「未完成」。
2. 对照 `docs/三周实施计划.md` 当日验收。
3. 改代码后更新本文件。
4. 未得到用户「提交」指示前不要 `git commit`。

## 当前指针

- **阶段：** W1 脚手架与数据进门（代码已交，含登录/导入/模型后台）
- **今日（2026-09-15）验收：** W1D1 compose + Java 健康检查已在云端验证（无 Docker 时依赖为 down，app 仍 up）
- **下一步：** 有 Docker 时联调导入；然后 W2 Worker
- **分支：** `dev`
- **禁止：** 四平台爬虫、报告当事实输出、把 Key 写入仓库

## 计划清单

| ID | 交付 | 状态 |
|----|------|------|
| W1D1 | docker-compose + Spring Boot + 健康检查 | 已完成（代码；compose 需本机 Docker 拉起） |
| W1D2 | Vue3 `/login` `/app` `/admin` + JWT | 已完成 |
| W1D3 | RBAC + 首次启动打印管理员密码 | 已完成 |
| W1D4 | 项目 CRUD + Excel/CSV 导入 + 失败行 | 已完成（样例 CSV 120 条；xlsx 同样支持） |
| W1D5 | ChannelAdapter 空实现 + LLM 后台 | 已完成 |
| W1缓冲 | 权限码同名、回写 D6 | 已完成 |
| W2 | Python Worker + 清洗 ABSA | **下一步** |

## 本机自检

- `mvn -f apps/server/pom.xml -DskipTests compile` 通过
- `GET /api/v1/health` 返回 `{ code, message, data }`（含 mysql/redis/qdrant/minio）；无 compose 时依赖为 `down`，`app` 仍为 `up`
- `GET /actuator/health` 为 UP（不因依赖未起而拖垮进程）
- 无 Docker 的环境可先 `FLYWAY_ENABLED=false mvn -f apps/server/pom.xml spring-boot:run` 验健康检查；有 Docker 时保持默认 Flyway 并 `docker compose up -d`
- 前端：`pnpm -C apps/web dev`

## 未完成 / 下一步

1. 有 Docker 的机器上拉起 compose，用 `eval/fixtures/sample-reviews.csv` 导入验证  
2. W2：`apps/ai-worker` FastAPI 骨架与清洗任务  
