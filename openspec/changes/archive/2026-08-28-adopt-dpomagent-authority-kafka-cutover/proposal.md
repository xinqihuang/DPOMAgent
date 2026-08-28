> **Status: SUPERSEDED.** This precursor was not applied. Its corrected and expanded scope was implemented and accepted by `realign-phase1-phase5-to-dpomagent-authority`; this change is archived without syncing its obsolete deltas into the main specifications.

## Why

现有 Phase 1B 规划错误地把 DPOMBaseMCPServer 定义为 Diagnosis/Investigation 权威并准备退休
DPOMAgent，与已经实现且已验证的 LLM、ToolUse、调查状态机和持久化边界相冲突。需要固定
DPOMAgent 的诊断权威地位，仅将 DPOMAgent 到 SRE Intelligence 的事件传输从兼容 HTTP
Outbox 演进为 Kafka，同时消除无实际产品职责的工作区根治理仓库。

## What Changes

- **BREAKING**：撤销“DPOMBaseMCPServer 取代 DPOMAgent”的 Phase 1B 目标；DPOMAgent 持续作为
  Incident、Investigation、Run、Step、Observation、Hypothesis 和 Conclusion 的权威来源。
- 在 DPOMAgent 现有事务 Outbox 上增加 Kafka 发布适配器；兼容 HTTP 与 Kafka 复用同一 canonical
  Diagnosis Event、状态机、租约、重放和审计语义。
- 通过灰度、等价性验证和可回滚切换，将 DPOMAgent -> SRE 的主传输从 HTTP 改为 Kafka；兼容期结束
  后只退休 HTTP 适配器，不退休 DPOMAgent。
- 固定 DPOMBaseMCPServer 为无 LLM、无 Investigation 状态、无 RCA/ToolUse 决策、无业务编排的
  受约束工具服务。
- 将跨服务 ADR、Phase 文档、OpenSpec、contracts 和仓库无关脚本合并到 DPOMAgent；`D:\code`
  仅作为本地多仓工作区，不再作为 `AISREPlatformGovernance` 或 `AISREPlatformContracts` Git 根仓库。
- 旧 `complete-phase1-three-service-convergence` change 保留为被本变更取代的历史，不再 apply。

## Capabilities

### New Capabilities

- `ai-sre-service-boundaries`: 固定 DPOMAgent、DPOMBaseMCPServer、SRE Intelligence、DeepEval 与
  HuaweiCloudAlarmChangeGuard 的职责、权威数据和允许的交互边界。

### Modified Capabilities

- `diagnosis-event-outbox`: 在既有 HTTP Outbox 语义上增加 Kafka 传输、双路等价验证、灰度切换和
  HTTP 适配器退休要求。
- `investigation-agent`: 明确 DPOMAgent 是持续的 Investigation/Diagnosis 权威来源，并要求权威状态
  可恢复、可重放且不依赖原始 LLM 会话。

## Impact

- DPOMAgent：Outbox delivery port、Kafka adapter、配置、Flyway/MyBatis 状态、指标、重放和测试。
- SREIntelligenceService：Kafka 与 HTTP 必须进入同一摄取应用端口并保持幂等/冲突语义一致。
- DPOMBaseMCPServer：增加静态架构守卫，禁止 LLM、诊断状态和业务编排依赖进入。
- 平台治理：ADR、docs、OpenSpec、contracts 和验证脚本的权威位置改为 DPOMAgent 仓库；其他服务
  必须通过版本化制品或仓库内固定快照消费契约，不得依赖 `../contracts`。
- 运维：本地 Kafka 使用 9092；切换必须保留兼容窗口、回滚路径和可观测证据。
