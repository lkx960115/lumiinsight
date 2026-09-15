# LumiInsight — Agent 入口

本仓库是 **灯鉴 LumiInsight** 独立仓。动手前先读：

1. [`docs/ai/AI_START_HERE.md`](docs/ai/AI_START_HERE.md)
2. [`docs/技术栈与架构说明书.md`](docs/技术栈与架构说明书.md)
3. [`docs/三周实施计划.md`](docs/三周实施计划.md)

## 闸门

- 未得到用户「确认执行 / 开始写代码」前，不要生成业务代码。
- **禁止**把 API Key、密码写入仓库、文档示例或提交信息。
- 公开仓：`.env` 必须 gitignore；后台模型 Key 加密落库，不进 Git。
- LLM **必须**走可配置的多提供方，禁止把智谱 GLM 写死为唯一调用路径。
- 采集 MVP 只做 Excel/CSV 导入；四平台适配器保持接口、返回未实现。
- 报告结论必须带证据 ID；无证据不得当作事实输出。
- 工作台按产品标准交付（中文、一个主操作、先结论后明细），见 [`docs/ai/PRODUCT_UI.md`](docs/ai/PRODUCT_UI.md)。

详细规矩见 [`docs/ai/LOCAL_RULES.md`](docs/ai/LOCAL_RULES.md)。
