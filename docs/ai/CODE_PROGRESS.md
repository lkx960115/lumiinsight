# 代码编写进度（中断后从这里接）

> 给人或 Agent：先读本文件，再动手。不要重做「已完成」项。  
> 用户已确认按计划执行（闸门已开）。

## 怎么接上

1. 读本文件「当前指针」与「未完成」。
2. 对照 `docs/三周实施计划.md` 当日验收。
3. 改代码后更新本文件。

## 当前指针

- **阶段：** W3D1 已交
- **本机 Docker：** mysql:5.7 占 3306、本机 Redis 占 6379；Docker Hub 直连会 `Service Unavailable`。用 `./scripts/compose-up.sh`（DaoCloud 镜像 + `docker pull`，避免 experimental `docker compose` 的 dockerfile.v0）。MySQL 映射 3307，Redis 映射 16379。Docker Desktop 需给 MySQL 8 / Qdrant 放开 seccomp，否则会无法建线程、反复重启。
- **下一步：** W3D2 单品报告模板（执行摘要、声量、情感、方面、原声、建议）
- **分支：** `dev`
- **禁止：** 四平台爬虫、报告当事实输出、把 Key 写入仓库

## 计划清单

| ID | 交付 | 状态 |
|----|------|------|
| W1 | 登录、RBAC、项目、导入、LLM 后台 | 已完成 |
| W2D1 | FastAPI 骨架 + Java 调空清洗 + 状态机 | 已完成 |
| W2D2 | 清洗去重/广告/脱敏 | 已完成 |
| W2D3 | 去水可解释标签 + 方面词典生效 | 已完成 |
| W2D4 | 情感 + ABSA | 已完成 |
| W2D5 | 按用途路由多模型 | 已完成 |
| W2D6 | 一键清洗分析、失败重试、工作台图表 | 已完成 |
| W3D1 | 证据表、按方面拉回原评、向量写入（失败则关键词） | 已完成 |

## 本机怎么跑（避开已有容器）

1. 复制 `.env.example` → `.env`（`MYSQL_PORT=3307`，`REDIS_PORT=16379`）
2. `./scripts/compose-up.sh`（国内镜像；不要直接 `docker compose up` 撞 Docker Hub）
3. `mvn -f apps/server/pom.xml spring-boot:run`
4. `cd apps/ai-worker && python3 app/main.py`（标准库启动，避免本机 uvicorn/pydantic 卡住）
5. `pnpm -C apps/web dev`（本机 Vite/esbuild 会卡住，已改 WASM 打包，第一次约半分钟）
6. 用启动日志里的 admin 密码登录（新权限 `pipeline:execute` 需重新登录）
7. 建项目，导入样例后点「清洗并分析」。图表按有效评论统计情感饼图与方面分布。点方面条或筛选「方面」可拉回对应原评。失败任务可在项目页重试。分析完成后会写证据并尝试写入向量；没配向量模型或 Qdrant 不可用时，仍可按方面关键词找回。改 Java 后请重启 IntelliJ（Flyway 会建证据表）；改前端后需停掉 5173 再 `node scripts/dev.mjs`，页面强制刷新。

健康检查：`GET /api/v1/health` 应看到 mysql/redis/qdrant/minio/worker。
