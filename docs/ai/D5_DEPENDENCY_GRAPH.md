# D5 依赖图谱 — lumiinsight

```
User/RBAC
   └── Project
          ├── ImportJob ──► File(MinIO) ──► Review
          │                                    ├── 清洗标记
          │                                    └── AnalysisResult ──► Aspect
          ├── LlmRoute（分析时读取，不反向依赖项目）
          ├── Evidence ◄── Review + ReportSection
          └── Report
```

## 连锁

| 改这里 | 会影响到 |
|--------|----------|
| 方面词典 | 已分析历史不自动重算，需重跑 Job |
| LLM 路由 | 进行中的 Job 仍用启动时快照（建议 Job 记录 modelCode） |
| Review 删除 | 必须级联或阻断：证据、向量、报告引用 |
| 项目删除 | 导入文件、评论、报告一并策略删除（高风险） |
| 提供方 Key | 不改历史报告，只影响新调用 |

表结构未建：本图是逻辑依赖。代码周补表名到 [D6](D6_REPO_INDEX.md)。
