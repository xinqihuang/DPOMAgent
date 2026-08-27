# DPOMAgent — Investigation/Diagnosis 权威运行时

DPOMAgent 已实现 production/development 双 Profile 诊断引擎和 Diagnosis Event v1 HTTP Outbox，
并持续作为 Investigation/Diagnosis 的唯一权威来源。Phase 1B 只把 DPOMAgent 到 SRE 的主传输从
HTTP Outbox 灰度迁移到 Kafka；权威边界以 [平台 ADR](docs/platform/ADR.md) 为准。

## 定位

### 生产区域（production profile）
- Phase 1A 当前链路中，`DPOMBaseMCPServer` 提供只读证据，`DPOMAgent production profile` 负责调查编排。
- Phase 1B 切换后，`DPOMAgent` 继续拥有 Incident/Investigation/Run/Step、假设、结论、预算、
  checkpoint、审计、Kafka 发布和 Portal REST/SSE；`DPOMBaseMCPServer` 始终只提供证据工具。
- 生产侧不要求原始源码；允许使用与发布版本匹配、经过边界裁剪的 `CodeGraph` 结果。
- 禁止 RAG/Embedding/Vector DB；禁止任意 Shell；禁止生产写操作；禁止自动执行修复或生成脚本；
  只允许人审批后的显式动作。

### 研发区域（development profile）
- 集中部署一套 `DPOMAgent development profile`，不为每个仓库部署独立 Agent。
- 配套 Repository Registry、`CodeGraph`（colbymchenry/codegraph，stdio MCP）、精确 release/commit 源码快照和研发侧 LLM。
- 接收生产侧证据包，校验完整性、版本和脱敏元数据后，结合准确源码进行最终 RCA。
- `CodeGraph`（`colbymchenry/codegraph`）是源码导航/结构化上下文，不包装成向量检索或 RAG。

### 跨区域交接
- OBS 是**受控证据传输通道**，不是知识库。
- 上传内容只能是版本化、限量、脱敏的 Diagnostic Evidence Package：告警、时间窗、拓扑/调用链、
  日志模板与代表样本、指标趋势摘要、CodeGraph 摘要、假设/矛盾/降级信息、服务/环境/release/commit、
  校验和与 schemaVersion。
- 禁止上传源码、AK/SK、Token、Cookie、原始大批量日志、无边界 dump。
- “满足升级条件”与“允许上传”分离：系统可以判断 `escalationEligible`，但 OBS 上传必须有显式 approval gate。

## 硬边界（Hard Boundaries）

- No RAG / Embedding / Vector DB。
- No arbitrary shell execution tool。
- No automatic production execution（诊断/修复脚本仅生成 Artifact，DPOMAgent 不执行）。
- No source / credential upload（证据包禁止源码、AK/SK、Token、Cookie、原始大批量日志、无边界 dump）。
- DPOMAgent 不新增 Docker/K8s/Helm/HA；Kafka 仅限 Diagnosis Event/Progress 传输 adapter，
  core domain 不得依赖 Kafka 类型。

## 技术栈

JDK21、Maven 3.9+、Spring Boot 3.4.5、Spring MVC + Virtual Threads、Spring AI 1.0.4、
Spring RestClient、MySQL + Flyway + MyBatis、Jackson、JUnit5/Mockito/AssertJ。

## 模块

- `agent-common`：跨模块共享的内部 DTO、Port、枚举与异常。
- `agent-adapter`：LLM / Runtime / CodeGraph 适配器。
- `agent-core`：调查编排、假设、证据、工作区、工具、脚本、持久化、证据交接。
- `agent-web`：唯一可执行 jar（REST 控制器、配置、迁移、composition root）。

## 开发顺序

1. `AGENTS.md` → `CLAUDE.md` → `openspec/config.yaml` → 当前 Change → 一张 Task Card。
2. 测试先行，逐项实现并把 `tasks.md` 对应项更新为 `[x]`。

## Diagnosis Event v1 兼容评测出站

迁移窗口内，`COMPLETED` / `INCONCLUSIVE` 调查会在终态事务内写入不可变 Diagnosis Event v1 与 `PENDING` outbox；
`FAILED` / `CANCELLED` 不产生评测事件。网络投递和内部重放默认都关闭，默认启动不装配 HTTP 端口、投递 worker
或重放控制器。

投递启用示例（secret 至少 32 UTF-8 字节，destination 必须是无 query/user-info 的 HTTPS URI）：

```text
DPOM_EVALUATION_DELIVERY_ENABLED=true
dpom.evaluation.delivery.destination=https://evaluation.example/internal/diagnosis-events
dpom.evaluation.delivery.hmac-secret=<secret-manager-reference>
dpom.evaluation.delivery.connect-timeout=2s
dpom.evaluation.delivery.read-timeout=5s
dpom.evaluation.delivery.max-attempts=5
dpom.evaluation.delivery.max-event-age=1d
dpom.evaluation.delivery.batch-size=20
```

内部重放单独启用：

```text
DPOM_EVALUATION_REPLAY_ENABLED=true
dpom.evaluation.replay.hmac-secret=<different-secret-manager-reference>
dpom.evaluation.replay.timestamp-window=5m
dpom.evaluation.replay.nonce-ttl=10m
```

事件状态为 `PENDING → IN_FLIGHT → DELIVERED`；有界重试回到 `PENDING`，永久拒绝、幂等冲突、内容完整性失败
或重试耗尽进入可查询的 `DEAD`。重放仅接受 `eventId/operatorRef/reason`，只允许 `DEAD → PENDING`，不会替换
eventId、幂等键或规范内容。上线顺序为先部署 V12 且保持两个开关关闭，观察 outbox 创建，再配置 HTTPS/HMAC
并启用投递；回滚时关闭投递和重放开关即可，已持久化事件不需要删除或重建。该通道是 Phase 1A 兼容路径，
不是第二套源权威；Kafka 与 HTTP 始终发布同一份 DPOMAgent 已冻结权威事实。
