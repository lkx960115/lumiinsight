# 灯鉴 LumiInsight

AI 驱动的灯具全网评论采集与消费者洞察报告平台（参赛 / 产品仓）。

**当前状态：** 第一周已入库；W2 清洗空实现已通。本机 Docker 请用独立 MySQL 8（默认 3307），不要复用已有 mysql:5.7。

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

本机若已有 **mysql:5.7 占用 3306**，不要把灯鉴接到那个库。Compose 默认把独立的 **MySQL 8** 映射到 **3307**（与现有 minio:19001 也不冲突）。

1. `git pull`，复制 `.env.example` → `.env`（不要提交）。  
2. **不要**直接 `docker compose up`（会打 Docker Hub，报 `dockerfile.v0` / Service Unavailable）。执行：

   ```bash
   chmod +x scripts/compose-up.sh
   ./scripts/compose-up.sh
   ```

   镜像默认走 DaoCloud。若仍失败：Docker Desktop → Settings → Docker Engine 增加 `"registry-mirrors": ["https://docker.m.daocloud.io"]`，Apply & Restart 后再跑脚本。  
3. 启动后端：`mvn -f apps/server/pom.xml spring-boot:run`。  
   首次启动日志会打印本地管理员账号密码（只一次，不要写进 README）。  
4. 启动 Worker：`cd apps/ai-worker && python3 -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt && uvicorn app.main:app --port 8090`。  
5. 启动前端：`pnpm -C apps/web dev`，浏览器打开 http://localhost:5173 。  
6. 建项目后导入 `eval/fixtures/sample-reviews.csv`；可点「触发清洗（空实现）」。  
7. 管理端配置 LLM Key 后再跑真实分析（W2 后续）。

## 安全

不要在 Issue、README、提交里粘贴 Key 或初始密码。模型密钥只放本机 `.env` 或后台加密配置。
