# ADR-001：AI For SRE Phase 1 四服务架构基线

- 状态：Accepted
- 决策日期：2026-08-25
- 范围：Phase 1 服务边界、兼容迁移和验收口径
- 后续路线图：`phases/`
- 数据与评测详细设计：`ADR/ADR-002-sre-intelligence-data-evaluation-closed-loop.md`

## 1. 项目目标

面向华为云生产环境建设可审计、可回放、可持续评价的 AI For SRE 闭环：

```text
主动巡检 -> 异常发现 -> 智能调查 -> 根因分析 -> 缓解建议 -> 人工审批
         -> 受控执行 -> 恢复验证 -> 经验沉淀 -> Replay 评价 -> 能力改进
```

Phase 1 只建立安全、版本化、可回放的诊断到评价链路，不一次性实现完整数据治理、Release Gate 或自动改进。

## 2. 决策

Phase 1 的目标架构包含四个核心后端部署单元。DPOMAgent 是独立的 Diagnosis/Investigation
服务；DPOMBaseMCPServer 只提供工具能力；数据服务和评测控制面不再拆分为更多微服务：

```text
Portal
  | REST / SSE
  v
DPOMAgent
  | LLM / ToolUse / Diagnosis Orchestrator / Investigation Runtime
  |-- MCP / bounded tool calls --> DPOMBaseMCPServer
  |                              Huawei Cloud evidence / CMDB / OBS
  |-- MCP / bounded tool calls --> Drain3MCPServer / CodeGraph
  |-- approved change request --> HuaweiCloudAlarmChangeGuard
  | Kafka: diagnosis-event / diagnosis-progress
  v
SRE Intelligence Service
  | ODS -> DWD -> DWS -> ADS
  | Batch / Replay / Java Rule Judge / Aggregation
  | bounded versioned HTTP
  v
DeepEval Service
  | stateless LLM-as-a-Judge
  v
JudgeResult -> SRE Intelligence 持久化、聚合和审计
```

Portal 只是交互与展示入口，不是 Incident、Investigation、Eval Run 或 Dataset 的权威数据源。

HuaweiCloudAlarmChangeGuard 是与诊断链路隔离的受控生产写操作边界，不属于 LLM 可直接调用的
通用工具面。

### 2.1 DPOMAgent

DPOMAgent 是在线 Diagnosis/Investigation 权威系统记录和智能诊断编排服务，负责：

- Incident、Investigation、Run、Step、Observation、Hypothesis、Conclusion；
- LLM Provider 隔离、ToolUse、Diagnosis Orchestrator、调查预算、Checkpoint、恢复和审计；
- 调用 DPOMBaseMCPServer、Drain3MCPServer、CodeGraph 等有界工具获取证据；
- 持久化源状态后，通过 Outbox 发布版本化 Kafka Diagnosis Event 和 Diagnosis Progress；
- 向 Portal 提供有界 REST/SSE 诊断进度。

DPOMAgent 的权威事实必须可从持久化状态恢复和重放，不得依赖原始 LLM 会话。

### 2.2 DPOMBaseMCPServer

DPOMBaseMCPServer 是无模型、无诊断状态、无业务编排的工具与生产证据边界，负责：

- 华为云 APM、CES、AOM、LTS、CCE 等只读能力与供应商 DTO 隔离；
- CMDB/拓扑、版本化 codegraph 代码证据和受控 OBS Artifact；
- 通过版本化、受约束的 MCP/HTTP 工具契约向 DPOMAgent 提供能力。

它不得持有 LLM Provider/API Key，不得执行 ToolUse 决策、RCA、Diagnosis Orchestrator、
Investigation 生命周期管理或业务流程编排；也不负责 Dataset、Judge 聚合、失败归因、
Release Gate，且不提供通用生产写工具。

### 2.3 SRE Intelligence Service

SRE Intelligence Service 是数据与评测控制面，负责：

- ODS/DWD/DWS/ADS 逻辑数据层及其 MySQL 权威元数据；
- Diagnosis Event 幂等摄取、冲突隔离、证据索引和来源血缘；
- Spring Batch、Incident Case、Bronze/Silver/Gold 和 Dataset 生命周期；
- Replay、确定性 Java Rule Judge、DeepEval 调度、JudgeResult 持久化与聚合；
- 后续 Phase 的人工一致性、失败归因、能力缺口、建议和 Release Gate。

它不得直连其他服务数据库、持有华为云生产凭据、执行生产写操作或在 Java 进程内实现语义 Judge。

### 2.4 DeepEval Service

DeepEval Service 是 Python + FastAPI 的无状态语义评价引擎，负责执行固定、版本化的 Judge 定义。它只返回单项 JudgeResult，不拥有 Dataset、调度、持久化、聚合、Release Gate、生产凭据或诊断生命周期状态。

### 2.5 本地联调端口基线

为避免多个服务使用框架默认端口导致启动冲突，本地开发、跨服务联调和验收环境统一使用以下端口：

| 服务或基础设施 | 本地端口 |
|---|---:|
| DPOMAgent | 8080 |
| HuaweiCloudAlarmChangeGuard | 8081 |
| DPOMBaseMCPServer | 8082 |
| SREIntelligenceService | 8083 |
| DeepEvalService | 8084 |
| Drain3MCPServer | 8100 |
| Kafka | 9092 |
| MySQL | 3306 |

各服务的本地启动配置、联调脚本和示例 URL SHALL 与该表保持一致，不得依赖 Spring Boot、Uvicorn
或 MCP Server 的隐式默认端口。生产及其他部署环境 MUST 通过环境变量、配置中心、Kubernetes Service
或等价的服务发现机制覆盖地址和端口；服务间调用不得硬编码 `localhost` 或本表中的端口。

## 3. 当前实现事实与 Phase 1 目标的关系

2026-08-23 已完成一个兼容性评测垂直切片：

```text
DPOMBaseMCPServer -> DPOMAgent -> HTTP Outbox -> SRE Intelligence -> DeepEval
     只读证据          调查系统记录          评测控制面          两个语义 Judge
```

该切片已经证明版本化 Diagnosis Event、幂等摄取、冲突关闭、Rule Judge、两个语义 Judge、PASS/FAIL/INCOMPLETE 聚合和持久化 Replay。它是 Phase 1 的可运行迁移基线；其中 DPOMAgent 作为 Investigation/Diagnosis 权威来源的职责保持不变，需要迁移的是事件传输路径，而不是诊断所有权。

DPOMAgent 持续持有现有及新增的 Investigation 数据。HTTP Outbox 是 Phase 1A 兼容传输基线，Kafka Outbox 是 Phase 1B 目标传输路径。迁移必须先增加 characterization coverage，证明契约、幂等、重放和可观测性等价，再灰度切换传输路径；不得迁移 Investigation 权威来源，也不得静默重写或删除现有记录。

## 4. Phase 1 迁移顺序

1. 固定现有 DPOMAgent -> SRE HTTP 垂直切片的行为和验收证据。
2. 定义 Diagnosis Event、Evidence Manifest 和 JudgeResult 等版本化跨服务契约及正反 fixtures。
3. 固定 DPOMAgent 的 Investigation/Diagnosis 权威模型，并为诊断状态、Outbox 和恢复路径补齐持久化与 characterization coverage。
4. 在 DPOMAgent 增加 Kafka Outbox 发布适配器；DPOMBaseMCPServer 继续只提供无状态工具能力。
5. 让 Kafka 与兼容 HTTP 适配器进入 SRE 的同一个摄取应用端口。
6. 验证两条传输路径具有相同的幂等、冲突隔离、顺序、重放和观测语义。
7. 按明确的数据兼容、灰度和回滚标准，将 DPOMAgent -> SRE 的主传输从 HTTP 切换为 Kafka。
8. 兼容窗口结束后退休 HTTP 传输适配器；DPOMAgent 及其 Investigation/Diagnosis 权威职责不退休。

## 5. Phase 1 验收标准

- [x] 兼容垂直切片可持久化、评价和回放一个真实 Diagnosis Event。
- [x] 重复 HTTP 投递幂等，身份相同但摘要冲突时失败关闭。
- [x] Rule 与语义 Judge 独立持久化，缺失或失败不会被推断为通过。
- [x] 报告保留事件、调查、运行、证据、Judge 和组件版本血缘。
- [ ] DPOMAgent 持久化并可恢复权威 Investigation Runtime，且恢复不依赖原始 LLM 会话。
- [ ] DPOMAgent 在源状态和 Outbox 同一事务提交后发布版本化 Kafka Diagnosis Event。
- [ ] Kafka 与兼容 HTTP 通过同一 SRE 摄取策略并通过等价性测试。
- [ ] Portal 可通过有界 SSE 查看诊断进度，载荷不包含证据正文或敏感信息。
- [ ] HTTP -> Kafka 传输切换具有验证、灰度、回滚和明确的 HTTP 适配器退休标准。
- [ ] 架构测试证明 DPOMBaseMCPServer 不包含 LLM、RCA、Investigation 状态或诊断业务编排。

只有全部验收项具备客观仓库证据后，`docs/phases/PHASE-1.md` 才能标记为 Complete。

## 6. Phase 1 非目标

- 完整 Bronze/Silver/Gold 人工治理和 Dataset 生命周期；
- 六 Judge 全目录及 Judge 与人工一致性校准；
- Failure Attribution、Capability Gap、Improvement Recommendation 和 Release Gate；
- Improvement Agent；
- 跨诊断、证据、Judge 和改进产物的统一 Diagnostic Report Contract、标准模板与多格式确定性投影；
- Knowledge/RAG、Embedding、Vector DB；
- 自动生产缓解、通用 Shell 或生产写工具；
- Flink、Spark、Iceberg、MLflow 等分布式数据平台。

这些能力分别进入 Phase 2–5，见 `phases/`。其中 Phase 5 的实施路线见
`phases/PHASE-5.md`，专门规范诊断报告的权威 JSON 契约、完整性语义、证据/Judge
血缘、不可变修订和 Markdown/Portal/HTML/PDF 同源投影。

## 7. 全局约束

- 诊断与生产执行分离，所有生产操作必须经过显式人工审批。
- 固定流程与智能推理分离；原始事实与知识/评价投影分层。
- 服务之间只通过版本化 API、事件或不可变 Artifact 交互，禁止跨服务读写数据库表。
- 同一事件身份只能绑定一个规范摘要；冲突、未知版本、缺失证据、超时和无效 Judge 输出一律失败关闭。
- 原始诊断对象和 Investigation/Diagnosis 权威状态由 DPOMAgent 持有；SRE 只持有数据治理与评价侧事实和来源引用。
- DPOMBaseMCPServer 始终是被调用的工具服务，不得成为 Agent、LLM 宿主、诊断系统记录或业务编排服务。
- 所有 Agent 能力必须可评价、可审计、可从持久化事实重放，不依赖原始 LLM 会话。
- 日志和指标不得包含凭据、Prompt、原始模型输出、证据正文或高基数任意标识。

## 8. 文档优先级

发生冲突时按以下顺序解释产品意图：

1. 本 ADR：Phase 1 服务边界与迁移原则；
2. `docs/ADR/ADR-002-*`：数据与评测闭环详细决策；
3. `docs/phases/PHASE-N.md`：阶段范围、状态和退出标准；
4. `docs/openspec/changes/*`：实施需求与 Backlog；
5. 各仓库 README、AGENTS 和旧任务卡：仅约束对应仓库当前实现，若与已接受 ADR 冲突，应先通过专门迁移任务更新，不能据此反向改写目标架构。

## 9. 汇报材料风格

后续 PPT 默认采用白底灰阶、红色关键强调、结论式标题、一页一个核心观点，以架构图、流程、矩阵和分层表达为主，避免深色科技风、渐变和发光效果。
