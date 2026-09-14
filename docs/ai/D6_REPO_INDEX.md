# D6 Repo 级理解 — lumiinsight

## 目录（2026-09-14）

```
lumiinsight/
├── AGENTS.md
├── README.md
├── .gitignore
├── .env.example
├── docs/
│   ├── 技术栈与架构说明书.md
│   ├── 三周实施计划.md
│   └── ai/
│       ├── AI_START_HERE.md
│       ├── D1_ARCHITECTURE_BASELINE.md
│       ├── D2_BOUNDARY_MODULE_MAP.md
│       ├── D3_INJECTABLE_RULES.md
│       ├── D4_TACIT_KNOWLEDGE.md
│       ├── D5_DEPENDENCY_GRAPH.md
│       ├── D6_REPO_INDEX.md          ← 本文件
│       ├── SYSTEM_CONTEXT.md
│       ├── LOCAL_RULES.md
│       └── DOMAIN_LUMI.md
├── apps/web/          （占位，无代码）
├── apps/server/
├── apps/ai-worker/
└── eval/
```

## 怎么找

| 问题 | 去哪 |
|------|------|
| 技术选型 / 多模型 | 说明书第 4、8 章；D1 |
| 能否爬虫 | D2、LOCAL_RULES |
| 三周做什么 | `docs/三周实施计划.md` |
| 方面词 / Excel 列 | DOMAIN_LUMI |
| 源码类名 | **尚无**，写代码后回写本节 |

## 代码周后必须补

- `apps/server` 包结构与启动类  
- 前端路由表文件路径  
- Worker 路由列表  
- 核心表名  
