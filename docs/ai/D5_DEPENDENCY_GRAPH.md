# D5 依赖图谱 — lumiinsight

```
User/RBAC
   └── Project
          ├── ImportJob ──► File(MinIO) ──► Review（认模板列与商家导出列名）
          │                                    ├── 清洗标记
          │                                    └── AnalysisResult ──► Aspect
          ├── LlmRoute（分析时读取，不反向依赖项目）
          ├── Evidence ◄── Review + ReportSection
          ├── Ask（只读本项目原评，无证据不编；问过的留在项目里）
          ├── Alert（按月对照负向变多 / 新槽点 / 声量骤降，结论带原评；对照记录留在项目里）
          └── Report ──► 摘要改写（失败回退） + 问原评章节 + 导出 PDF / 表格 / 文稿
LlmRoute ──► LlmUsage（只读展示，不回写报告）
```

## 连锁

| 改这里 | 会影响到 |
|--------|----------|
| 方面词典 | 已分析历史不自动重算，需重跑 Job |
| LLM 路由 | 进行中的 Job 仍用启动时快照（建议 Job 记录 modelCode） |
| Review 删除 | 必须级联或阻断：证据、向量、报告引用 |
| 项目删除 | 导入文件、评论、报告、问原评和预警记录一并删除（高风险） |
| 提供方 Key | 不改历史报告，只影响新调用 |
| 报告导出 | PDF / 表格 / 文稿都带引用编号，对应文末原评。PDF 需本机有中文字体。导出失败先重启后台 |
| 用量 | 只读；不展示 Key 和评论文本 |

表结构：见 [D6](D6_REPO_INDEX.md) 核心表。逻辑依赖仍如上图。
