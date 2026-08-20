# Design: 告警中台（agent-alarm）

## Context

见 `proposal.md - Why`。DPOMAgent 现有模块 `agent-common / agent-adapter / agent-core / agent-web`，依赖方向 `agent-web → agent-core → agent-adapter → agent-common`。持久化已统一为 MySQL + Flyway + MyBatis（见归档 change `2026-08-19-replace-jdbc-with-mybatis`）。DPOMBaseMCPServer 已提供 AOM/CES/APM/LTS 只读查询。本设计在边界内新增 `agent-alarm` 模块，不引入 Kafka/Docker/K8s/HA、不引入 RAG/Embedding、不引入第三方 HTTP 客户端、不自动执行生产操作。

## Goals / Non-Goals

**Goals:**
- 在 `agent-alarm` 内闭环实现接入→治理→关联→通知→查询五层，可独立测试。
- 通过 `agent-common` 中的端口倒置，让 `agent-core` 消费 Incident 触发诊断，避免 `agent-alarm → agent-core` 反向依赖。
- 复用 DPOMBaseMCPServer 只读网关与既有 Spring RestClient/MyBatis/Flyway 技术栈，零新基础设施。

**Non-Goals:**
- 不做告警中台 UI（前端不在本变更范围）。
- 不做 LLM 驱动的告警关联/根因（关联为确定性时间窗+拓扑规则；RCA 仍由 agent-core 既有诊断编排承担）。
- 不做高可用/消息中间件/流式引擎；单实例 Spring MVC + 虚拟线程足以覆盖本系统告警量级。
- 不在 `agent-alarm` 内持有华为云凭据或直接调用华为云 SDK；华为云告警经 webhook 推送或经 DPOMBaseMCPServer 只读网关轮询获取。
- 不自动执行任何生产写操作或 Shell。

## Decisions

### D1 模块与依赖
- 新增 Maven 模块 `agent-alarm`，根 `pom.xml` 注册 `<module>agent-alarm</module>`，版本与依赖管理沿用父 POM。
- 依赖方向：`agent-alarm → agent-common`；`agent-core → agent-alarm`（agent-core 装配并消费 Incident 触发端口）；`agent-web` 作 composition root 装配全部。`agent-alarm` MUST NOT 依赖 `agent-core` 或 `agent-adapter`。
- `agent-common` 新增端口 `AlarmIncidentTriggerPort`（事件触发诊断的抽象）与告警领域共享 DTO/枚举（严重度、来源服务、事件状态）。LLM/Runtime/CodeGraph DTO 不进入 `agent-alarm`。

### D2 内部分层
- `ingestion`：webhook 控制器 + 轮询调度（`@Scheduled` + 虚拟线程）+ 来源适配（AOM/CES/APM/LTS 标准化器）。
- `governance`：去重（指纹哈希 + 时间窗，Redis 仅缓存指纹存在性以减压，权威状态在 MySQL）、压缩采样、分组、分级（可配置映射表）。
- `correlation`：时间窗 + 拓扑邻接的确定性聚合，产出 `AlarmIncident`；拓扑邻接信息由可配置拓扑源提供（初始为静态配置，后续可接既有拓扑证据）。
- `notification`：规则匹配引擎 + 渠道发送（邮件、IM webhook，统一 Spring RestClient）+ 认领/抑制/静默 + 处置工件生成（委托 `agent-core` 既有 `ScriptArtifact` 能力，经端口，工件带 `REQUIRES_APPROVAL`）。
- `query`：告警/事件分页查询、订阅回调注册与推送。
- 每层只依赖下层接口与 `agent-common`；跨层经接口，便于单测替换。

### D3 持久化
- 新增 Flyway 迁移（版本号续接既有最大版本，避免冲突）：`alarm`、`alarm_incident`、`alarm_incident_member`、`notification_rule`、`notification_record`、`alarm_suppression`、`alarm_audit`。
- MyBatis XML Mapper，显式 `resultMap`/`constructor`，禁 `SELECT *`，保留字反引号，遵循 `2026-08-19-replace-jdbc-with-mybatis` 的 D1–D7 约定。
- Redis 仅用于告警查询缓存与去重指纹存在性缓存，不持久化权威状态。

### D4 接入方式
- webhook：`POST /api/v1/alarms/webhook`，按来源服务分发到对应标准化器；幂等（按指纹去重兜底）。
- 轮询：`@Scheduled` 周期调用 DPOMBaseMCPServer 只读查询（经既有 MCP/REST 适配，不在 `agent-alarm` 引入华为云 SDK），增量拉取（按上次拉取时间游标）。轮询与 webhook 共用同一治理管道。
- 两条来源均记录接入方式与来源服务，可审计。

### D5 关联确定性
- 关联引擎为纯函数式确定性规则：时间窗（可配置）∩ 拓扑邻接（可配置拓扑源）。不调用 LLM、不计算向量相似度。聚合严重度取成员最高且记录来源。
- 触发诊断经 `AlarmIncidentTriggerPort`：`agent-core` 实现该端口启动 Investigation；端口未装配时 `agent-alarm` 安全降级（记录跳过，不抛异常）。

### D6 通知与处置边界
- 出站通知统一经 Spring RestClient；不引入第三方 HTTP 客户端。
- 处置动作只生成 `ScriptArtifact`（经端口委托 agent-core）并带 `REQUIRES_APPROVAL`；`agent-alarm` 不执行工件、不持有 AK/SK。
- 认领/抑制/静默/通知/处置全链路写 `alarm_audit`。

### D7 测试策略
- 测试先行：每层先写 JUnit5 + Mockito + AssertJ 单测；Mapper 契约测试沿用 H2 + 真实 MySQL 8.0 契约（Testcontainers 或受控外部实例）。
- webhook/轮询/关联/通知端到端用 `@SpringBootTest` 切片 + stub DPOMBaseMCPServer。

## Risks / Trade-offs

- [单实例吞吐上限] → 本系统告警量级由 webhook+轮询限流与去重压缩兜底；不引入 Kafka，以可配置批处理与虚拟线程吸收峰值。若未来量级超限，需新 ADR 引入消息中间件（当前 Non-Goal）。
- [轮询与 webhook 重复] → 指纹去重为权威兜底，两来源共用治理管道，重复告警合并而非双写。
- [拓扑源静态] → 初始拓扑邻接为静态配置，覆盖范围有限；后续可接既有拓扑证据，接口已预留，不破坏 spec。
- [端口未装配降级] → `agent-alarm` 在诊断联动未启用时安全跳过触发，不阻塞告警中台自身职责。
- [关联确定性 vs 漏关联] → 确定性规则可解释可审计，但可能漏关联非邻接根因；RCA 仍交由 agent-core 诊断编排兜底，本层只负责事件化。

## Migration Plan

1. 新增 `agent-alarm` 模块与父 POM 注册；CI 先构建空模块。
2. Flyway 迁移新建告警相关表（续版本号），clean-install baseline 兼容。
3. 逐层落地：ingestion → governance → correlation → notification → query，每层带单测与契约测试。
4. `agent-common` 增端口，`agent-core` 实现并装配；`agent-web` 装配控制器与调度。
5. 回滚：模块未装配时 DPOMAgent 既有诊断能力不受影响；迁移可按 Flyway 标准回退（新表为增量，不改动既有表）。

## Open Questions

- 告警 webhook 的认证/签名校验机制（华为云告警事件签名）是否由部署边界统一处理，还是需在 `agent-alarm` 内校验？倾向部署边界处理，但需与运维确认。
- 拓扑邻接源的初始静态配置格式与维护责任归属，待与现有拓扑证据团队对齐。
