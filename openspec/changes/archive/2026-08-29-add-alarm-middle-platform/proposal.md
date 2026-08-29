## Why

DPOMAgent 目前能对单个故障做调查编排与 RCA，但缺少告警的统一接入、治理、关联与处置入口：华为云 AOM/CES/APM/LTS 告警分散在 DPOMBaseMCPServer 的只读查询里，没有标准化、去重、分组、事件化和通知闭环。需要一个"告警中台"把告警从原始信号收敛为可诊断的 Incident，并驱动通知与处置编排，从而让诊断引擎从"被动调查"升级为"告警驱动闭环"。

## What Changes

- 在 DPOMAgent 内新增 `agent-alarm` Maven 模块，承载告警中台全部职责；不引入新基础设施（无 Kafka、无 Docker/K8s、单实例 Spring MVC + 虚拟线程）。
- **统一接入与治理**：REST webhook 接收华为云告警事件 + 定时轮询 DPOMBaseMCPServer 兜底；将 AOM/CES/APM/LTS 告警标准化为统一 `Alert` 模型；做去重、压缩、分组、分级；持久化到 MySQL（Flyway 迁移 + MyBatis）；提供查询与订阅 REST API。
- **告警关联与事件化**：基于时间窗 + 拓扑/调用链把告警聚合成 `AlarmIncident`；通过 `agent-common` 中的 Port 倒置，由 `agent-core` 消费 Incident 触发现有 Investigation 编排，`agent-alarm` 不依赖 `agent-core`。
- **通知与处置编排**：路由/分派规则、通知渠道（邮件、IM webhook，统一用 Spring RestClient）、认领、抑制、静默、闭环跟踪；处置动作只生成 `ScriptArtifact` + `REQUIRES_APPROVAL`，DPOMAgent 不自动执行生产操作。
- 模块依赖方向：`agent-web → agent-core → agent-alarm → agent-common`；`agent-alarm` 内部分层 `ingestion → governance → correlation → notification → query`。
- LLM/Runtime/CodeGraph DTO 不泄漏进 `agent-alarm`；告警中台本身不做 LLM 推理，关联规则为确定性策略（时间窗 + 拓扑邻接），不引入 RAG/Embedding。

## Capabilities

### New Capabilities

- `alarm-ingestion-governance`: 华为云多服务告警的接入、标准化、去重/压缩/分组/分级、持久化与查询订阅。
- `alarm-correlation-event`: 基于时间窗与拓扑/调用链的告警关联与 Incident 事件化，以及对接诊断编排的触发端口。
- `alarm-notification-handling`: 通知路由/分派、多渠道发送、认领/抑制/静默与处置闭环跟踪。

### Modified Capabilities

无。

## Impact

- 新增 `agent-alarm` 模块及其在根 `pom.xml` 的 `<module>` 注册；`agent-core` 增加对 `agent-alarm` 的依赖以消费 Incident。
- 新增 MySQL 表（`alarm`、`alarm_incident`、`alarm_incident_member`、`notification_rule`、`notification_record`、`alarm_suppression` 等）及 Flyway 迁移；Redis 仅用于告警查询缓存。
- 新增 REST 端点（webhook 接入、告警/Incident 查询、订阅、通知规则与抑制管理）；`agent-web` 增加控制器与配置装配。
- 复用 DPOMBaseMCPServer 只读网关作为华为云告警数据源，不引入华为云凭据或 SDK 到 `agent-alarm`；轮询通过既有 MCP/REST 适配。
- 不新增前端框架（UI 不在本变更范围）、不新增第三方 HTTP 客户端、不新增 Kafka/消息中间件；遵守无生产写执行、无任意 Shell、无 RAG 边界。
