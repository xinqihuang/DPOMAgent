# add-investigation-operability 验收报告

日期：2026-08-15
Change：add-investigation-operability（未归档，等待独立验收）

## 实现文件

- 依赖：`agent-web/pom.xml`（+`spring-boot-starter-actuator`、+`micrometer-registry-prometheus`）
- 配置：`agent-web/src/main/resources/application.yml` 与 `src/test/resources/application.yml`（`management.endpoints.web.exposure.include=health,prometheus`；`management.health.probes.enabled=true`；`management.endpoint.health.group.liveness.include=livenessState`、`management.endpoint.health.group.readiness.include=readinessState,db,executorCapacity`；`management.endpoint.health.status.http-mapping` DOWN/OUT_OF_SERVICE→503；`server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase`；executor await；adapter-health-ttl；logback console 含 `[%X{correlationId:-}]`）
- 最小 DAO（唯一 core 改动，不改已发布 migration、不改 agent-core 业务逻辑）：`agent-core/.../InvestigationDao.updateStatusIfActive`（`WHERE status NOT IN ('COMPLETED','INCONCLUSIVE','FAILED','CANCELLED','WAITING_FOR_HUMAN')` 返回 affected rows，活动态集合与 `findNonTerminal()`/状态机一致）
- 优雅停机：`agent-web/config/AsyncConfig.java`（waitForTasksToCompleteOnShutdown + awaitTerminationSeconds + strictEarlyShutdown，单一 owner，无自定义 SmartLifecycle）
- 指标：`agent-web/metrics/{ErrorCodes,InvestigationMetrics,AdapterMetrics}.java` + `Metered{ModelClient,CodeGraphClient,LogTemplateMinerClient,RuntimeEvidenceClient}.java` + `AdapterMeteringBeanPostProcessor.java`（就地包装，避免 `@Primary` 自注入循环与 `@MockitoBean` 歧义，覆盖 4 个接口全部方法）
- 执行器 gauge + Clock：`agent-web/config/OperabilityConfig.java`（Clock 标 `@Role(INFRASTRUCTURE)` 避免 BPP 早期注入告警）
- 健康：`agent-web/config/HealthIndicatorsConfig.java`（executorCapacity + llm/codegraph/drain3 被动指示器）；`agent-web/health/AdapterHealthRegistry.java`（固定枚举、ConcurrentHashMap、可注入 Clock、TTL 过期归 UNKNOWN，不存 Throwable/message/URL/参数/响应）；`agent-web/controller/HealthController.java`（external 段被动派生 UP/DOWN/UNKNOWN）
- correlationId：`agent-web/filter/CorrelationIdFilter.java` + `agent-web/config/CorrelationIdConfig.java`（最高优先级，先于 `RequestSizeLimitFilter`，`RequestSizeLimitConfig` order 调整为 HIGHEST+1）
- 编排：`agent-web/service/InvestigationApplicationService.java`（submitted/terminated/duration 埋点、correlationId 异步传播、每次执行尝试在 finally 恰 stop 一次 timer）；`agent-web/service/InvestigationReconciler.java`（recovered 计数 + `updateStatusIfActive` 条件更新）

## 测试证据（mvn clean verify）

BUILD SUCCESS：195 tests（common 8 + adapter 11 + core 63 + web 113），0 failure，0 error，6 skipped。
新增 42 测试（全部通过）：
- AdapterHealthRegistryTest（3）、AdapterMetricsTest（5）、CorrelationIdFilterTest（3）、LowCardinalityLabelTest（1）、ExecutorMetricsTest（1）、InvestigationMetricsFaultToleranceTest（1）
- InvestigationMetricsTest（2）：submitted/terminated/duration 增量 + 幂等重放不重复计数
- InvestigationExecutionOutcomeTest（8）：COMPLETED/INCONCLUSIVE/FAILED/WAITING/执行异常/终态落库后异常/CANCELLED 各路径 timer 增量严格为 1、terminal counter 按路径语义一次、WAITING 不被异常补偿覆盖、CANCELLED 稳定映射为 (FAILED,FAILED)、errorCode 有限白名单
- InvestigationClosureEdgeTest（5）：updateRunning 异常走统一补偿 + timer 恰一次 + MDC 清理；metrics 记录阶段异常不破坏 api_request 收口与 MDC 清理；recordSubmitted 异常不阻止派发（无孤儿）；startExecution 异常不破坏业务（sample 可缺失）；recordTerminated 异常不覆盖 503/CAPACITY_FULL
- ReconcilerMetricTest（1）、CorrelationIdPropagationTest（1）
- HealthGroupTest（4，RANDOM_PORT）、ActuatorExposureTest（3，RANDOM_PORT）
- GracefulShutdownTest（4）：有界 drain / shutdown 幂等 / 不无限等待 / shutdown 后新任务 RejectedExecutionException 且不运行
- 扩展：LogSafetyTest、HealthControllerTest

## Actuator 暴露面

- 仅 `/actuator/health`（含 liveness/readiness）与 `/actuator/prometheus`；`/actuator/metrics` 404。
- health `show-components=always`、`show-details=never`：只暴露状态与组件名，不含 URL/异常/响应/凭据。
- `management.endpoint.health.group.liveness.include=livenessState`；`management.endpoint.health.group.readiness.include=readinessState,db,executorCapacity`（外部被动指示器排除）；UP→200、DOWN/OUT_OF_SERVICE→503。
- 部署前置：`/actuator/**` 由 CCE 网络策略/Service 限制暴露（本 Change 不实现认证）。

## 指标清单（前缀 dpom.）

- `dpom.investigation.submitted`（counter）
- `dpom.investigation.terminated`（counter，标签 status/resultType；**单次进程执行路径恰一次**，best-effort）
- `dpom.investigation.execution.duration`（timer，标签 status/resultType/errorCode；**每次执行尝试恰好记录一次**）
- `dpom.executor.queue.size` / `dpom.executor.active` / `dpom.executor.pool.size` / `dpom.executor.queue.capacity`（gauge，无标签）
- `dpom.reconciliation.recovered`（counter，实际恢复数）
- `dpom.adapter.call.duration`（timer，标签 adapter/errorCode；成功 NONE、超时 TIMEOUT、其余有限白名单）
- 标签 value 枚举：status∈{COMPLETED,INCONCLUSIVE,FAILED,REJECTED,WAITING_FOR_HUMAN}（WAITING 仅计时器）、resultType∈{ROOT_CAUSE_FOUND,INCONCLUSIVE,INSUFFICIENT_EVIDENCE,FAILED,REJECTED,NONE}、adapter∈{llm,codegraph,drain3,runtime}、errorCode∈{NONE,TIMEOUT,PROVIDER_ERROR,NOT_FOUND,NOT_READY,ERROR,INVALID_ARGUMENT,ILLEGAL_STATE,EXECUTION_ERROR,CAPACITY_FULL,RECONCILED_AFTER_RESTART}

## 关键语义说明（metrics best-effort 与 DB durable truth 边界）

- **DB 状态是持久化 truth，Micrometer 指标是 best-effort observability**（内存、进程内、重启丢失）；不声称跨进程/跨崩溃严格 exactly-once。
- 单次进程执行路径不重复：`execute()` 每次执行尝试在 `finally` 恰好 stop 一次 timer；terminal counter 仅当该次执行观察/产生终态时记一次；幂等重放（不派发）与重复 reconciliation 不重复。
- 收口分层：`updateRunning` 置于受控 try 内（其异常走统一 outcome/errorCode/活动态补偿）；`finally` 用真实嵌套 try/finally（内层「api_request 收口（best-effort）+ metrics（best-effort）」、外层 finally 无条件 `MDC.remove`），业务收口/指标/日志任一 RuntimeException 后都清理 MDC。
- 容错集中封装：`InvestigationMetrics`/`AdapterMetrics` 吞掉一切 MeterRegistry 异常（`Timer.Sample` 可为 null）；服务对 submit/reject/startExecution 的指标调用 best-effort 包裹；adapter 真实 delegate 调用为主语义，指标/被动健康故障不阻止调用、不改变返回值、不替换 delegate 原异常。
- CANCELLED 稳定映射：status 与 resultType 均稳定为 FAILED（不产生 FAILED/INCONCLUSIVE 组合）。
- `updateStatusIfActive`（活动态条件更新）**只仲裁 reject / 异步异常补偿 / reconciliation**，不仲裁 coordinator 成功终态（成功终态由状态机迁移写入）；WAITING_FOR_HUMAN 为暂停态，不被 reject/异常补偿覆盖为 FAILED。
- 适配器装饰：BeanPostProcessor 就地包装（单一 bean），只按异常类别映射 errorCode，不使用类名/message。
- 被动适配器健康：仅由装饰器在真实业务调用时写入「成功 + 时间戳」，health 端点只读注册表，不主动调用外部。

## 边界审计

No RAG/Embedding/Vector DB；无 arbitrary shell；无自动生产执行；不执行诊断/修复脚本；单实例 Java Web；不新增第二常驻控制面；agent-core 仅最小新增 DAO 方法（不改其业务逻辑与已发布 migration）；agent-adapter-* 不改；未开发 V2。

## 已知风险

- 被动健康在首次业务调用前/静默期为 UNKNOWN（随业务流量刷新）。
- `/actuator/**` 无认证（内网 + CCE 网络策略前置）；prometheus 仅含低基数标签，无请求体/日志/密钥。
- metrics 为 best-effort：进程崩溃/重启会丢失尚未导出的计数；跨进程 exactly-once 不在承诺范围内。

## 结论

本 Change 不归档，停止等待独立验收。
