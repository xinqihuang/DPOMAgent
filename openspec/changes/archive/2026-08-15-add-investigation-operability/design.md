## Context

现状审计（本 Change 前）见 `add-investigation-service-api` 已归档实现：

- `HealthController`（`/api/v1/health`）：`status` 由 `dbStatus()`（`SELECT 1`）与 `executorAvailable()`（active < max || queue.remainingCapacity > 0）派生；`external` 静态 UNKNOWN/NOT_CONFIGURED。
- `AsyncConfig`：`investigationExecutor`（core2/max4/queue10，AbortPolicy），队列满由 `dispatchOrReject` 抛 503 并事务化补偿 FAILED/REJECTED。
- `InvestigationReconciler`：位于 **agent-web**（`com.dpom.agent.web.service`），`ApplicationReadyEvent` 把非终态调查标记 FAILED（结论恰一次、api_request 同步 FAILED），无计数。
- 日志安全：`InvestigationApplicationService`/`GlobalExceptionHandler` 已结构化最小化日志（investigationId + 稳定 errorCode，不记 Throwable/异常 message/请求体/原始日志/密钥/SQL/路径）。
- 请求体上限：`RequestSizeLimitFilter`（413 PAYLOAD_TOO_LARGE）。
- 适配器接口在 agent-common，实现在 agent-adapter-*，agent-web 已依赖 agent-adapter-llm/codegraph/runtime（Core 只依赖接口）。
- 无 Actuator/Micrometer；无 correlationId；无优雅停机（仅 Spring Boot 默认）。

本 Change 全部改动落在 **agent-web**：reconciler/调查服务/执行器均在 agent-web，埋点与装饰器也在 agent-web，不修改 agent-core 与 agent-adapter-*，保持 core 依赖方向。

## Goals / Non-Goals

**Goals:** 标准 liveness/readiness（503/200 语义）、低基数业务/资源/适配器指标（单次执行路径不重复，best-effort）、被动适配器健康、correlationId 全链路、有界优雅停机。

**Non-Goals:** 认证授权、UI、多实例分布式调度、真实 CCE/LTS/AOM 接入、RAG/Embedding/Vector DB、任意 shell、自动生产执行、V2；不新增第二常驻控制面；不改 `/api/v1/investigations` 与 `/api/v1/health` 既有结构。

## Decisions

### D1 依赖与暴露面：actuator + prometheus，仅 health/prometheus
`spring-boot-starter-actuator` + `micrometer-registry-prometheus`；`management.endpoints.web.exposure.include=health,prometheus`（**不含** `metrics`）。
`management.endpoint.health.show-components=always`、`show-details=never`：`/actuator/health` 只暴露聚合状态 + 组件名，不暴露 URL/异常文本/响应/凭据。
`/api/v1/health` 结构不变，`external` 段由静态改为被动派生（见 D4）。
部署前置条件：由 CCE 网络策略/Service 限制 `/actuator/**` 的暴露范围；本 Change 不实现认证。

### D2 健康 group 组成与 HTTP 语义
- `management.endpoint.health.group.liveness.include=livenessState` → `/actuator/health/liveness`（进程存活即 UP，200）。
- `management.endpoint.health.group.readiness.include=readinessState,db,executorCapacity` → `/actuator/health/readiness`。UP→200；DOWN/OUT_OF_SERVICE→503（Spring Boot 默认 HTTP 映射，显式确认 `DOWN=503, OUT_OF_SERVICE=503`）。
- 外部被动适配器指示器（llm/codegraph/drain3）**明确排除**在 readiness/liveness group 之外，只进 `/actuator/health` 组件列表与 `/api/v1/health` 的 external 段。
- 现有 `HealthController` 聚合逻辑复用为 `db`/`executorCapacity` 两个 `HealthIndicator`，避免双份实现。

### D3 指标：`dpom.*` 显式埋点 + 有限枚举（best-effort observability）
指标（前缀 `dpom.`）：`investigation.submitted`（counter）、`investigation.terminated`（counter，标签 status/resultType，**单次进程执行路径恰一次**）、`investigation.execution.duration`（timer，标签 status/resultType/errorCode，**每次执行尝试在 finally 恰 stop 一次**）、`executor.{queue.size,active,pool.size,queue.capacity}`（gauge，**无标签**，单例执行器）、`reconciliation.recovered`（counter，计**实际恢复数**）、`adapter.call.duration`（timer，标签 adapter/errorCode）。
语义边界：DB 状态是持久化 truth，Micrometer 为 best-effort（内存、进程内、重启丢失），**不声称跨进程/跨崩溃严格 exactly-once**；只保证单次进程执行路径不重复、幂等重放（不派发）与重复 reconciliation 不重复。`updateStatusIfActive`（活动态条件更新）只仲裁 reject/异步异常补偿/reconciliation，**不仲裁 coordinator 成功终态**（成功终态由状态机迁移写入）。
容错集中封装：`InvestigationMetrics`/`AdapterMetrics` 吞掉一切 MeterRegistry 异常，`Timer.Sample` 可为 null；服务对 submit/reject/startExecution 的指标调用做 best-effort 包裹；adapter 真实 delegate 调用是主语义，指标/被动健康故障不阻止调用、不改变返回值、不替换 delegate 原异常。
执行器 gauge 用 `MeterBinder` 绑定 `investigationExecutor`，避免线程级高基数。

### D4 适配器：`@Primary` 装饰器 + 被动健康注册表（均在 agent-web）
agent-web 用装饰器包装各适配器接口并注册为 `@Primary`：计时后调用真实实现；`errorCode` 由异常**类别**推导（成功 NONE、超时 TIMEOUT、`ModelProviderException`→PROVIDER_ERROR、`SnapshotNotFoundException`→NOT_FOUND、`SnapshotNotReadyException`→NOT_READY、其余 ERROR），**不读 message/参数/响应**。
装饰器同时把「最近一次调用结果 + 时间戳」写入 `AdapterHealthRegistry`（内存，按 adapter 维度）。
`AdapterHealthIndicator` 只读该注册表：从未调用→UNKNOWN；窗口内成功→UP、失败→DOWN；超过 `dpom.api.adapter-health-ttl`（如 5m）→UNKNOWN。health 端点**不主动调用**任何适配器，不产生费用/流量/副作用。
不改 agent-core/agent-adapter-*；Core 仍只依赖接口。

### D5 低基数护栏：只扫描 `dpom.*`，key 白名单 + value 枚举
标签 key 白名单 = {status, resultType, adapter, errorCode}；value 枚举：status∈{COMPLETED,INCONCLUSIVE,FAILED,REJECTED,WAITING_FOR_HUMAN}（WAITING_FOR_HUMAN 仅计时器使用，不计入 terminal counter）、resultType∈{ROOT_CAUSE_FOUND,INCONCLUSIVE,INSUFFICIENT_EVIDENCE,FAILED,REJECTED,NONE}、adapter∈{llm,codegraph,drain3,runtime}、errorCode∈{NONE,TIMEOUT,PROVIDER_ERROR,NOT_FOUND,NOT_READY,ERROR,INVALID_ARGUMENT,ILLEGAL_STATE,EXECUTION_ERROR,CAPACITY_FULL,RECONCILED_AFTER_RESTART}。
`LowCardinalityLabelTest` 只遍历 `MeterRegistry` 中 meter id 前缀为 `dpom.` 的自定义 meter（排除 JVM/HTTP 等内置 meter 的 area/id/cause/uri 标签），断言其 tag key ∈ 白名单 **且** tag value ∈ 对应有限枚举。

### D6 correlationId：`CorrelationIdFilter`（先于 body-limit）+ 全响应回显 + MDC
- `CorrelationIdFilter` 在 filter 链**最前**（order 早于 `RequestSizeLimitFilter`）：缺省生成 UUID；合法 `[A-Za-z0-9._-]{1,64}` 采用；非法→400 统一 ErrorResponse 并用**新生成**的 correlationId 作响应头。
- 过滤器在进入链前 `response.setHeader("X-Correlation-Id", id)` 并 `MDC.put`，`finally` `MDC.remove`。因顺序在前，`RequestSizeLimitFilter` 直接写的 413 响应也携带已设置的 correlationId 头。
- 异步传播：`submit()` 从 MDC/request attribute 读 id，作为参数传入 `execute(...)`；`execute` 内 `MDC.put("correlationId")` + `finally` 清理。
- logback pattern 增加 `[%X{correlationId:-}]`；investigationId 用日志占位符（可另写 MDC）。
替代方案：`TaskDecorator` 复制 MDC——可补充，但显式传参更可测。

### D7 优雅停机：executor 生命周期参数 + server.shutdown=graceful（无自定义 SmartLifecycle）
- `AsyncConfig.investigationExecutor` 追加：`setWaitForTasksToCompleteOnShutdown(true)`、`setAwaitTerminationSeconds(<bounded，如 30>)`、`setStrictEarlyShutdown(true)`（Spring 6.2+ 可用；apply 时确认版本，不可用则仅前两项）。
- `server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase=<bounded>`：先停 Web 层不再收新请求，再触发 executor 的 Spring 生命周期销毁。
- **单一 owner**：Spring 的 executor 生命周期完成 bounded drain；不新增 SmartLifecycle/`@PreDestroy` 手动 `shutdown()`，避免与内置生命周期重复或顺序竞态、避免 double shutdown。
- 停机期间新提交被拒→复用 `dispatchOrReject` 503 补偿；超时遗留非终态调查由下次启动 `InvestigationReconciler` 恢复为 FAILED。

## Risks / Trade-offs

- [Actuator 无认证暴露] → 仅 health/prometheus，health 无 details；`/actuator/**` 由 CCE 网络策略/Service 限制（部署前置，本 Change 不实现认证）。
- [被动健康 vs 主动探针] → 被动观测不产生外部费用/流量，但首次调用前/静默期为 UNKNOWN；运维在业务流量恢复后状态随之刷新。
- [高基数标签] → D5 只扫 `dpom.*`，key 白名单 + value 枚举，双保险；禁止 serviceCode/investigationId 等。
- [优雅停机超时丢任务] → 明确接受：超时在途任务由 reconciliation 恢复，禁止无限等待（与现有语义一致）。
- [装饰器覆盖遗漏某适配器方法] → 测试锁定各 adapter 调用均产生 timer 且 errorCode 正确。

