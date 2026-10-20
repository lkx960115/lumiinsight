# SYSTEM_CONTEXT — lumiinsight

内容层：有什么。结构见 D1/D2；规矩见 LOCAL_RULES。展开叙述见 [说明书](../技术栈与架构说明书.md)。

## 状态

W1 脚手架已在仓库：Java API + Vue 工作台/管理端。无分析流水线（W2）。联调依赖本机 Docker Compose。

## 技术栈版本（已锁定小版本）

- JDK 17 字节码（本机可用 JDK 21 编译）
- Spring Boot 3.3.5、MyBatis-Plus 3.5.9
- Vue 3.5 + Vite 5 + Element Plus 2
- Python Worker：W2，目标 3.11

## 业务域

灯具口碑：项目、评论导入、清洗（未做）、ABSA（未做）、证据（未做）、报告（未做）、后台模型配置（已做表单，未真调）。

## 权限模型

RBAC。预置角色：`admin`、`analyst`。项目级：负责人 + `project_member`。管理员看全部项目。

## 外部系统

| 系统 | MVP |
|------|-----|
| 智谱 / 其它 LLM | 后台配置后，W2 再调用 |
| 电商平台 | 不接，适配器抛 `CHANNEL_NOT_IMPLEMENTED` |
| 公司 OTM/MCP | 无依赖 |
