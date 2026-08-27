# AI For SRE 文档入口

本目录保存 Phase 1–5 的阶段路线图、详细架构决策和验收资料。不要根据单个旧仓库 README 或某张旧任务卡推断全局服务边界。

## 权威阅读顺序

1. `ADR.md`：Phase 1 服务架构、兼容基线和迁移原则。
2. `ADR/ADR-002-sre-intelligence-data-evaluation-closed-loop.md`：数据与评价闭环的详细领域决策。
3. `phases/PHASE-1.md` 至 `PHASE-4.md`：阶段状态、范围和退出标准。
4. `../../openspec/changes/realign-phase1-phase5-to-dpomagent-authority/`：当前实施需求、设计、任务和证据。

`openspec/` 子目录是从旧治理仓库迁入的历史规划快照，只用于追溯；当前 OpenSpec 以仓库根部
`openspec/` 为准。

## 统一术语

| 术语 | 含义 |
|---|---|
| 兼容垂直切片 | 已跑通的 DPOMAgent HTTP Outbox -> SRE -> DeepEval 实现；是迁移基线，不是最终拓扑 |
| Phase 1 目标 | DPOMAgent、DPOMBaseMCPServer、SRE Intelligence Service、DeepEval Service 四个核心后端部署单元 |
| Phase 1A | 已完成的兼容垂直切片和验收证据 |
| Phase 1B | 尚未完成的运行时归属收敛、Kafka 等价路径和 DPOMAgent 迁移 |
| Phase 2 | Case、Gold、Dataset、六 Judge 和人工一致性 |
| Phase 3 | 失败归因、能力缺口、建议和 Release Gate |
| Phase 4 | 受人工治理的 Improvement Agent |

Phase 1A/1B 只是里程碑标签，不是新增长期架构层级。Phase 1 整体在 Phase 1B 验收完成前保持 In Progress。

## 三服务目标

```text
Portal -> DPOMAgent -> Kafka -> SRE Intelligence Service -> HTTP -> DeepEval Service
                 |-> bounded tools -> DPOMBaseMCPServer
```

- DPOMAgent：在线 Investigation/Diagnosis 权威、LLM/ToolUse 编排和事件 Producer。
- DPOMBaseMCPServer：无模型、无诊断状态、无业务编排的证据工具边界。
- SRE Intelligence Service：ODS/DWD/DWS/ADS、Case、Dataset、Replay、聚合和治理控制面。
- DeepEval Service：无状态、版本化 LLM-as-a-Judge 执行器。

## 状态解释

文档中的 `[x]` 只表示存在对应实现和验收证据，不代表代码已经提交、仓库已经发布或后续迁移已经完成。阶段状态只能在该阶段全部退出标准具有客观仓库证据后更新。
