## Purpose

把华为云 AOM/CES/APM/LTS 多源告警统一接入、标准化为单一 Alert 模型，并完成去重、压缩、分组、分级、持久化与查询订阅，作为告警中台后续关联、事件化与通知处置的数据底座。

## ADDED Requirements

### Requirement: 多源告警接入

系统 SHALL 提供一个 REST webhook 端点接收华为云告警事件，并 SHALL 提供一个定时轮询任务从 DPOMBaseMCPServer 只读网关拉取 AOM/CES/APM/LTS 告警作为兜底。系统 MUST 能同时处理 webhook 推送与轮询拉取两种来源而不丢失告警。系统 SHALL 不在告警中台内持有华为云凭据或直接调用华为云 SDK。

#### Scenario: webhook 接收 AOM 告警
- **GIVEN** 一个符合华为云告警事件格式的 AOM 告警事件到达 webhook 端点
- **WHEN** 系统处理该事件
- **THEN** 系统返回 2xx 并将该告警纳入标准化流程

#### Scenario: 轮询兜底拉取 CES 告警
- **GIVEN** webhook 未送达且轮询任务到达调度周期
- **WHEN** 轮询任务从 DPOMBaseMCPServer 拉取到一条新 CES 告警
- **THEN** 该告警被纳入标准化流程且与 webhook 来源告警走同一治理路径

#### Scenario: 来源标识可追溯
- **GIVEN** 任一告警被持久化
- **WHEN** 查询该告警
- **THEN** 该告警记录其来源服务（AOM/CES/APM/LTS 之一）与接入方式（webhook/poll）

### Requirement: 告警标准化

系统 SHALL 将各源告警映射为统一 Alert 模型，至少覆盖：告警 ID、来源服务、资源标识、告警名称、严重度、状态、首次发生时间、最近发生时间、原始字段集合、接入时间。标准化 MUST 是原始告警的无损投影：原始字段集合 SHALL 保留来源全部字段，不得丢弃或拍平隐藏。系统 SHALL 拒绝无法识别来源服务的告警并记录可审计的拒绝原因。

#### Scenario: APM 告警标准化为统一模型
- **GIVEN** 一条 APM 告警包含 traceId、服务名、阈值、触发值等字段
- **WHEN** 系统执行标准化
- **THEN** 统一 Alert 模型包含上述字段的映射且原始字段集合保留全部 APM 字段

#### Scenario: 未知来源被拒绝
- **GIVEN** 一条来源服务不在 AOM/CES/APM/LTS 集合内的告警
- **WHEN** 系统执行标准化
- **THEN** 系统拒绝该告警、不持久化、并记录拒绝原因与原始事件摘要

### Requirement: 去重与压缩

系统 SHALL 对同一告警指纹（来源服务 + 资源标识 + 告警名称 + 严重度的稳定哈希）的重复告警做去重：在指定时间窗内重复到达的告警 MUST 合并为一条，递增发生计数并更新最近发生时间，而非新增记录。系统 SHALL 支持对同指纹告警的压缩采样：保留首末与代表性中间样本，不保留无界全量重复。

#### Scenario: 时间窗内重复告警合并
- **GIVEN** 同指纹告警已在 5 分钟去重窗内存在一条记录
- **WHEN** 同指纹告警再次到达
- **THEN** 系统递增已有记录的发生计数、更新最近发生时间，不新增告警记录

#### Scenario: 超窗告警新建
- **GIVEN** 同指纹告警上次到达时间已超过去重窗
- **WHEN** 同指纹告警再次到达
- **THEN** 系统新建一条告警记录

### Requirement: 分组与分级

系统 SHALL 支持按资源、服务、告警名称等维度对告警分组，并 SHALL 将各源严重度映射到统一严重度等级（如 Critical/Warning/Info）。分级规则 SHALL 可配置且变更可审计。

#### Scenario: 华为云严重度映射到统一等级
- **GIVEN** 一条 CES 告警携带华为云原始严重度
- **WHEN** 系统执行分级
- **THEN** 该告警被赋予统一严重度等级且映射规则版本可追溯

### Requirement: 持久化与查询订阅

系统 SHALL 将告警持久化到 MySQL，并通过 Flyway 迁移管理 schema、通过 MyBatis 访问。系统 SHALL 提供分页查询告警的 REST API，支持按来源服务、资源、严重度、状态、时间区间过滤。系统 SHALL 提供订阅 API，允许下游注册对指定过滤条件的告警事件回调。查询 SHALL 不阻塞接入路径。

#### Scenario: 按条件分页查询告警
- **GIVEN** 数据库中存在多条告警
- **WHEN** 客户端请求按严重度=Critical 且时间区间过滤的分页查询
- **THEN** 系统返回符合条件的告警分页结果与游标

#### Scenario: 订阅推送新告警
- **GIVEN** 下游已订阅严重度=Critical 的告警事件
- **WHEN** 一条 Critical 告警完成治理并持久化
- **THEN** 系统向订阅回调推送该告警事件

#### Scenario: 接入路径不被查询阻塞
- **GIVEN** 大量查询并发
- **WHEN** 新告警到达 webhook
- **THEN** 告警接入与持久化延迟不因查询负载显著增加
