# 代码编写进度（中断后从这里接）

> 给人或 Agent：先读本文件，再动手。不要重做「已完成」项。  
> 用户已于 2026-09-14 确认：**列出计划并按计划执行**（闸门已开）。

## 怎么接上

1. 读本文件「当前指针」与「未完成」。
2. 对照 `docs/三周实施计划.md` 当日验收。
3. 改代码后更新本文件。
4. 未得到用户「提交」指示前不要 `git commit`。

## 当前指针

- **阶段：** W1 已在本机写完脚手架与数据进门；下一步是 **W2（09-21）FastAPI Worker**
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
- 前端依赖已用 pnpm 装入；本环境 `esbuild --version` 会挂起，**未完成 Vite 生产构建**。接续时在本机执行：`pnpm --filter lumiinsight-web build` 或 `pnpm -C apps/web dev`
- 联调：`docker compose up -d` → 启动 Java → 看日志拿 `admin` 密码 → 打开 `http://localhost:5173`

## 未完成 / 下一步

1. 本机拉起 compose 并启动 Java，用 `eval/fixtures/sample-reviews.csv` 导入验证  
2. W2：`apps/ai-worker` FastAPI 骨架与清洗任务  
