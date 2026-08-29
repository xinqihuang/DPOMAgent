# Tasks: 告警中台（agent-alarm）

> 假设（待确认但不改变 spec/任务拆分）：webhook 认证/签名校验由部署边界统一处理，`agent-alarm` 仅预留配置钩子；拓扑邻接源初始为静态配置文件，接口预留后续接既有拓扑证据。每张任务卡先测试后实现。

## 1. 模块骨架与依赖

- [x] 1.1 新增 `agent-alarm` Maven 模块目录结构与 `pom.xml`，依赖 `agent-common`，在根 `pom.xml` 注册 `<module>`；CI 构建空模块通过。
- [x] 1.2 在 `agent-common` 新增告警领域共享类型：严重度等级、来源服务枚举、告警状态、事件状态，及端口 `AlarmIncidentTriggerPort`（事件触发诊断抽象）。先写单测覆盖枚举与端口契约。
- [x] 1.3 `agent-core` 增加对 `agent-alarm` 的依赖，实现 `AlarmIncidentTriggerPort`（启动 Investigation 并记录触发关系），端口未装配时 `agent-alarm` 安全降级单测。

## 2. 持久化迁移

- [x] 2.1 编写 Flyway 迁移（续版本号）新建 `alarm`、`alarm_incident`、`alarm_incident_member` 表；MyBatis XML Mapper + 显式 resultMap/constructor，禁 `SELECT *`，保留字反引号。先写 Mapper 契约测试（H2）。
- [x] 2.2 迁移新建 `notification_rule`、`notification_record`、`alarm_suppression`、`alarm_audit` 表及对应 Mapper；契约测试覆盖增删改查与审计写入。
- [x] 2.3 真实 MySQL 8.0 契约验证（Testcontainers 或受控外部实例），clean-install baseline 兼容确认。

## 3. 接入层（ingestion）

- [x] 3.1 定义统一 `Alert` 模型（record + Dto 后缀，无 Lombok）与来源标准化器接口；为 AOM/CES/APM/LTS 各写标准化器单测（无损投影、未知来源拒绝）。
- [x] 3.2 实现 webhook 控制器 `POST /api/v1/alarms/webhook`，按来源服务分发到标准化器，幂等；预留签名校验配置钩子（默认部署边界处理）。`@WebMvcTest` 切片测试。
- [x] 3.3 实现轮询调度（`@Scheduled` + 虚拟线程）经 DPOMBaseMCPServer 只读网关增量拉取（时间游标），与 webhook 共用治理管道；stub 网关端到端测试。

## 4. 治理层（governance）

- [x] 4.1 实现告警指纹（来源服务+资源+告警名称+严重度稳定哈希）与时间窗去重：窗内合并递增计数、超窗新建；Redis 仅缓存指纹存在性，权威状态在 MySQL。单测覆盖去重/合并/超窗。
- [x] 4.2 实现压缩采样（保留首末与代表性中间样本，无界全量不保留）单测。
- [x] 4.3 实现分组（按资源/服务/告警名称）与分级（可配置严重度映射表，版本可追溯、变更可审计）单测。

## 5. 关联与事件化（correlation）

- [x] 5.1 实现确定性关联引擎：时间窗 ∩ 拓扑邻接（静态配置拓扑源），产出 `AlarmIncident`（成员、关联依据、起止时间、聚合严重度）；纯函数单测覆盖聚合/不聚合/不调 LLM。
- [x] 5.2 实现 `AlarmIncident` 生命周期（Open/Acknowledged/Resolved）与超时未确认升级候选标记，状态变更写审计；单测覆盖状态流转与升级评估。
- [x] 5.3 实现触发端口调用：事件满足触发条件时经 `AlarmIncidentTriggerPort` 请求启动 Investigation，端口未装配安全降级；端到端测试（stub agent-core 实现）。

## 6. 通知与处置（notification）

- [x] 6.1 实现通知规则匹配引擎（按来源/资源/严重度/标签匹配多渠道），无匹配不发送；规则变更写审计。单测覆盖匹配/无匹配。
- [x] 6.2 实现邮件与 IM webhook 渠道发送，统一 Spring RestClient，记录每条通知发送结果与时间；单测用 MockRestServiceServer。
- [x] 6.3 实现认领、抑制（条件内暂停）、静默（时间区间暂停），有起止时间且可审计；静默/抑制期内跳过通知。单测覆盖。
- [x] 6.4 实现处置工件生成：经端口委托 `agent-core` 生成带 `REQUIRES_APPROVAL` 的 `ScriptArtifact`，`agent-alarm` 不执行工件、不持 AK/SK；单测断言不执行生产操作。

## 7. 查询与订阅（query）

- [x] 7.1 实现告警分页查询 REST API（按来源/资源/严重度/状态/时间区间过滤 + 游标），`@WebMvcTest` 切片测试。
- [x] 7.2 实现 `AlarmIncident` 查询与审计时间线查询 REST API；切片测试。
- [x] 7.3 实现订阅 API（注册过滤条件回调），新告警治理完成后推送；查询不阻塞接入路径（虚拟线程 + 异步推送）测试。

## 8. 装配与验收

- [x] 8.1 `agent-web` 装配 `agent-alarm` 控制器、调度、端口实现与配置属性；`@SpringBootTest` 全链路冒烟。
- [x] 8.2 Checkstyle + `mvn verify` 全量通过（方法体≤50 行、LOG 大写、中文 Javadoc、无 Lombok/WebFlux/Guava/commons-lang3）。
- [x] 8.3 端到端验收：webhook/轮询接入 → 治理去重 → 关联事件化 → 通知发送 → 处置工件（REQUIRES_APPROVAL）→ 闭环审计时间线，全链路用例通过。
