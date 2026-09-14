# D1 架构基线 — lumiinsight

> 给 AI 画地基。不得提出与本基线冲突的方案。完整说明见 [技术栈与架构说明书](../技术栈与架构说明书.md)。

## 1. 系统定位

| 项 | 值 |
|----|-----|
| 名称 | 灯鉴 LumiInsight |
| 角色 | 灯具评论导入、清洗、ABSA、可溯源报告 |
| 部署 | 先本地；Linux 后置 |
| GitHub | 公开仓 `lkx960115/lumiinsight` |

```
Vue ──REST/JWT──► Spring Boot ──HTTP──► FastAPI Worker ──► 多 LLM（后台配置）
                     │
                     ├── MySQL / Redis / MinIO / Qdrant
```

## 2. 技术栈（固定）

| 层 | 技术 | 约束 |
|----|------|------|
| 前端 | Vue3 + Vite + TS + Element Plus | 禁止 React；同一工程两套路由 |
| 后端 | JDK17 + Spring Boot 3 + MyBatis-Plus | 禁止无确认上 Boot 4 / 微服务拆分 |
| Worker | Python 3.11 + FastAPI | NLP/LLM 调用可在此 |
| LLM | 后台多提供方，`openai_compat` | **禁止**写死单一厂商 SDK 为唯一路径 |
| 向量 | Qdrant | 三周内可降级 |
| 构建 | 前 pnpm；后 Maven；Worker uv/pip | |

## 3. 分层（代码周必须落入）

| 模块 | 职责 | 禁止 |
|------|------|------|
| `apps/web` | 页面、权限指令、ECharts | 不写清洗/ABSA 算法 |
| `apps/server` | API、RBAC、导入、任务编排、报告装配、模型配置 | 不直连浏览器调 LLM |
| `apps/ai-worker` | 清洗、ABSA、embedding | 不做登录鉴权、不对外网关 |

## 4. LLM

见说明书第 8 章：Provider / Model / Route（主+备）。默认预置智谱，但必须可在后台改成任意 OpenAI 兼容端点。

## 5. 明确禁止

- K8s、服务网格、未确认的新中间件  
- 未授权爬取实现  
- 把 API Key 写入源码或文档  
- 报告无 evidenceId 仍当事实输出  
