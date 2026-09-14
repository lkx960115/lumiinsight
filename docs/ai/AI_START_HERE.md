# AI_START_HERE — lumiinsight

灯鉴独立仓的 AI 协作入口。对标 MCP 仓的六维上下文包；**对外完整口径**见 [技术栈与架构说明书](../技术栈与架构说明书.md)。

## 必读顺序

1. 本文件  
2. [代码进度（中断从这接）](CODE_PROGRESS.md)  
3. [D1 架构基线](D1_ARCHITECTURE_BASELINE.md)  
4. [D2 边界与模块地图](D2_BOUNDARY_MODULE_MAP.md)  
5. [D3 可注入规则](D3_INJECTABLE_RULES.md) + [LOCAL_RULES](LOCAL_RULES.md)  
6. 改业务语义时：[DOMAIN_LUMI.md](DOMAIN_LUMI.md)、[D4](D4_TACIT_KNOWLEDGE.md)  
7. 改链路时：[D5](D5_DEPENDENCY_GRAPH.md)、[D6](D6_REPO_INDEX.md)  
8. 排期：[三周实施计划](../三周实施计划.md)

## 六维包

| 维度 | 文档 | 解决什么 |
|------|------|----------|
| D1 | 架构基线 | 技术栈、分层、禁止换栈 |
| D2 | 边界地图 | 改哪里、不该动哪里 |
| D3 | 可注入规则 | 编码闸门 |
| D4 | 隐性知识 | 灯具词典、导入坑 |
| D5 | 依赖图谱 | 改一点影响谁 |
| D6 | 仓库索引 | 文件入口 |

## 规则优先级

1. 用户当轮明确指令  
2. `LOCAL_RULES.md` / D3（只能收紧）  
3. 说明书（人读权威）  
4. 六维其它文档  

## 当前仓库状态

**2026-09-14：W1 业务代码已写入 `apps/server` + `apps/web`。** 后续改动先读 [CODE_PROGRESS.md](CODE_PROGRESS.md)。未确认前仍不要把四平台空适配器改成爬虫。

## 高风险（须打标）

- 权限 / RBAC / 项目数据范围  
- LLM Key、加密、模型路由  
- 批量导入、批量删除  
- 采集适配器（即使空实现也勿改成爬虫）  
