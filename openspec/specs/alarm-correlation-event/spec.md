# alarm-correlation-event Specification

## Purpose

把治理后的告警按时间窗与拓扑/调用链邻接关系聚合成 AlarmIncident（事件），并通过倒置端口驱动 DPOMAgent 既有诊断编排，使告警中台从"告警列表"升级为"可诊断事件"，且不使告警中台依赖诊断引擎实现。

## Requirements

### Requirement: 告警关联成事件

系统 SHALL 将满足关联条件的告警聚合成一个 AlarmIncident。关联条件 SHALL 至少包括：时间窗（告警最近发生时间落在同一可配置窗口内）与拓扑邻接（资源之间存在已知拓扑/调用链边）。关联策略 MUST 为确定性规则，不使用 LLM 推理或 RAG/Embedding 相似度。每个 AlarmIncident SHALL 记录其成员告警、关联依据、起止时间与聚合严重度。

#### Scenario: 同窗同拓扑告警聚合
- **GIVEN** 三条告警的最近发生时间落在同一时间窗内且其资源在拓扑上邻接
- **WHEN** 关联引擎运行
- **THEN** 三条告警被聚合为同一个 AlarmIncident，关联依据记录时间窗与拓扑边

#### Scenario: 不满足关联条件不聚合
- **GIVEN** 两条告警时间窗不重叠且无拓扑邻接
- **WHEN** 关联引擎运行
- **THEN** 两条告警分属不同 AlarmIncident 或保持未事件化

#### Scenario: 关联不使用 LLM
- **GIVEN** 关联引擎配置就绪
- **WHEN** 任意告警集合进入关联
- **THEN** 关联结果仅由确定性时间窗与拓扑规则决定，不调用任何 LLM 或向量检索

### Requirement: 事件生命周期与升级

系统 SHALL 为 AlarmIncident 维护生命周期状态（Open/Acknowledged/Resolved）。系统 SHALL 在事件持续未确认超过可配置阈值时标记升级候选。事件状态变更 MUST 可审计。

#### Scenario: 超时未确认标记升级候选
- **GIVEN** 一个 Open 事件超过升级阈值未被确认
- **WHEN** 升级评估运行
- **THEN** 该事件被标记为升级候选并记录评估时间

### Requirement: 对接诊断编排的触发端口

系统 SHALL 在 `agent-common` 中定义一个"事件触发诊断"端口（接口），由 `agent-core` 实现，`agent-alarm` 仅依赖该端口抽象。当事件满足触发条件时，系统 SHALL 通过该端口请求启动 Investigation，而不在 `agent-alarm` 内编排诊断。`agent-alarm` MUST 不依赖 `agent-core` 的实现类型。

#### Scenario: 事件触发诊断调查
- **GIVEN** 一个事件满足诊断触发条件且触发端口已由 agent-core 装配
- **WHEN** 事件被判定可触发
- **THEN** 系统通过端口请求启动 Investigation 并记录触发关系，告警中台不直接编排诊断

#### Scenario: 端口未装配时安全降级
- **GIVEN** 触发端口未装配（如未启用诊断联动）
- **WHEN** 事件被判定可触发
- **THEN** 系统记录触发跳过与原因，不抛出异常、不阻塞告警中台
