# Tasks: Add Investigation Operability

（先测试后实现；每项含对应测试或验收命令。改动集中在 agent-web；agent-core 仅最小新增 DAO 方法 `updateStatusIfActive`（活动态条件更新），不改已发布 migration、不改 agent-adapter-*。）

## 1. 依赖与配置

- [x] 1.1 agent-web 引入 spring-boot-starter-actuator 与 micrometer-registry-prometheus（验收：`mvn -pl agent-web -am compile` 成功）
- [x] 1.2 application.yml：management.endpoints.web.exposure.include=health,prometheus；health group（liveness/readiness 组成）；DOWN/OUT_OF_SERVICE→503 映射；server.shutdown=graceful + timeout-per-shutdown-phase；executor wait/await/strict-early 参数；correlationId 头；adapter-health-ttl；指标前缀（验收：`mvn test` 上下文加载成功）

## 2. 健康探针

- [x] 2.1 测试 liveness group 仅 livenessState，进程存活 200 UP（`HealthGroupTest`）
- [x] 2.2 测试 readiness group = readinessState + db + executorCapacity：UP→200、DOWN/OUT_OF_SERVICE→503（`HealthGroupTest`）
- [x] 2.3 实现 ReadinessHealthIndicator（DB `SELECT 1` 且 executor available）并复用现有 HealthController 聚合为 db/executorCapacity 两个 HealthIndicator
- [x] 2.4 测试外部被动适配器指示器不参与 readiness/liveness group（`HealthGroupTest`）
- [x] 2.5 测试 `/api/v1/health` 结构兼容（status/db/executor/external），external 段为被动派生 UP/DOWN/UNKNOWN（`HealthControllerTest` 回归）
- [x] 2.6 测试 Actuator 仅暴露 health/prometheus，不暴露通用 `/actuator/metrics`（`ActuatorExposureTest`）

## 3. 指标

- [x] 3.1 测试 submitted counter 递增（`InvestigationMetricsTest`）
- [x] 3.2 测试 terminated counter 以 status/resultType 为标签、**单次进程执行路径恰一次**（best-effort；幂等重放不派发不重复计数）（`InvestigationMetricsTest`）
- [x] 3.3 测试 execution.duration timer 标签仅 status/resultType/errorCode（`InvestigationMetricsTest`）
- [x] 3.4 实现 InvestigationApplicationService 埋点（submitted / terminated 单次路径一次 / duration 每次执行尝试 finally 恰 stop 一次）
- [x] 3.5 测试 executor gauge（queue.size/active/pool.size/queue.capacity）实时值（`ExecutorMetricsTest`）
- [x] 3.6 实现 executor MeterBinder（无标签，绑定 investigationExecutor）
- [x] 3.7 测试 reconciliation.recovered 计**实际恢复的 investigation 数**：空扫描/重复运行不递增（`ReconcilerMetricTest`）
- [x] 3.8 实现 InvestigationReconciler 埋点（实际恢复数，位于 agent-web）
- [x] 3.9 测试 adapter.call.duration 以 adapter + errorCode（NONE/TIMEOUT/白名单）为标签，**无 result 标签**（`AdapterMetricsTest`）
- [x] 3.10 实现 agent-web `@Primary` 适配器装饰器（llm/codegraph/drain3/runtime），不读参数/响应/异常文本，不改 agent-adapter-*
- [x] 3.11 测试执行结果全路径（COMPLETED/INCONCLUSIVE/FAILED/WAITING_FOR_HUMAN/执行异常/终态落库后异常）timer 增量严格为 1、terminal counter 按路径语义一次、errorCode 有限白名单（`InvestigationExecutionOutcomeTest`）
- [x] 3.12 测试 WAITING_FOR_HUMAN 暂停态不被 reject/异常补偿覆盖为 FAILED（`InvestigationExecutionOutcomeTest` + DAO `updateStatusIfActive`）
- [x] 3.13 指标容错集中封装：`InvestigationMetrics`/`AdapterMetrics` 吞掉 MeterRegistry 异常、`Timer.Sample` 可 null、adapter delegate 主语义不被指标/健康故障改变或替换；submit/reject/startExecution 指标调用 best-effort（`InvestigationMetricsFaultToleranceTest`、`AdapterMetricsTest`、`InvestigationClosureEdgeTest`）

## 4. 低基数标签护栏

- [x] 4.1 测试只扫描 `dpom.*` 自定义 meter（排除 JVM/HTTP 内置）：tag key ∈ {status,resultType,adapter,errorCode} **且** tag value ∈ 对应有限枚举（`LowCardinalityLabelTest`）

## 5. 被动适配器健康

- [x] 5.1 测试从未调用 → UNKNOWN（`AdapterHealthIndicatorTest`）
- [x] 5.2 测试窗口内成功→UP、失败→DOWN、超过 ttl→UNKNOWN（过期语义）（`AdapterHealthIndicatorTest`）
- [x] 5.3 测试 health 端点不主动调用适配器，组件状态不含 URL/异常文本/响应/凭据（`AdapterHealthIndicatorTest`）
- [x] 5.4 实现 AdapterHealthRegistry（装饰器写入「最近结果 + 时间戳」）+ AdapterHealthIndicator（只读注册表）

## 6. correlationId 与结构化日志

- [x] 6.1 测试缺省生成 UUID、合法值采用、非法值返回 400 + 新生成 correlationId 响应头（`CorrelationIdFilterTest`）
- [x] 6.2 测试所有成功/错误响应（含 413）均回显 `X-Correlation-Id`（`CorrelationIdFilterTest`）
- [x] 6.3 实现 CorrelationIdFilter（filter 链最前，先于 RequestSizeLimitFilter；MDC + 响应头 + finally 清理）
- [x] 6.4 测试异步执行传播 correlationId：异步线程日志与请求相同（`CorrelationIdPropagationTest`）
- [x] 6.5 实现 submit→execute 显式传参 + async 内 MDC 写入/清理
- [x] 6.6 测试结构化日志含 correlationId + investigationId + 稳定 errorCode，且不含密钥/原始日志/请求体/异常 message/堆栈（扩展 `LogSafetyTest`）
- [x] 6.7 调整 logback pattern 含 `[%X{correlationId:-}]`

## 7. 优雅停机

- [x] 7.1 测试停机停止新派发、有界等待在途任务、超时返回（不无限等待）（`GracefulShutdownTest`）
- [x] 7.2 测试 executor 关闭单一 owner：重复关闭不抛异常、不二次等待（无 double shutdown）（`GracefulShutdownTest`）
- [x] 7.3 实现 AsyncConfig executor 生命周期参数（waitForTasksToCompleteOnShutdown + awaitTerminationSeconds + strictEarlyShutdown 按版本可用性）+ server.shutdown=graceful；不新增 SmartLifecycle/手动 shutdown
- [x] 7.4 测试 shutdown/early-shutdown 后 execute 新任务抛 RejectedExecutionException 且不运行（`GracefulShutdownTest`）

## 8. 最终验收

- [x] 8.1 `mvn clean verify`：BUILD SUCCESS，全部测试通过
- [x] 8.2 更新 `docs/add-investigation-operability-acceptance-report.md`（新测试名 + 最终统计 + 边界审计），不归档、等待独立验收

