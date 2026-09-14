# D2 边界与模块地图 — lumiinsight

## 1. 本仓负责

| 能力 | 位置（代码周） |
|------|----------------|
| 工作台 + 管理端 UI | `apps/web` `/app` 与 `/admin` |
| 用户登录、RBAC、项目、导入 | `apps/server` |
| 多模型配置与调用路由 | `apps/server` + Worker 客户端 |
| 清洗 / ABSA / 向量 | `apps/ai-worker` |
| Agent 文档 | `docs/ai` |

## 2. 本仓不负责

| 能力 | 说明 |
|------|------|
| 公司 OGSM / OTM / MCP | 在 `ogsmProject`，禁止把灯鉴做成其子模块 |
| 四平台真实采集 | 仅接口占位 |
| SSO / 飞书登录 | MVP 不做 |
| 生产 Linux 运维 | 后置 |

## 3. 模块依赖（单向）

```
web → server → MySQL/Redis/MinIO/Qdrant
server → ai-worker
ai-worker → LLM Provider（按 server 下发的运行时配置，不自己读用户表）
```

禁止：web → worker；worker → 用户权限表；平行「再起一个 Java NLP 服务」除非评审。

## 4. 适配器边界

`ChannelAdapter` 四平台：只允许空实现 + 明确错误码。把空实现改成爬虫视为越界，必须先停下来确认。
