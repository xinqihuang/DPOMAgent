## Why

add-investigation-service-api 已归档并通过独立验收，调查 API、Drain3/CodeGraphContext/LLM 与真实回归已跑通；
但单实例 Java Web 仍缺生产级可观测性与生命周期治理——没有标准健康探针、业务指标、请求关联标识与优雅停机。
在接真实华为云 LTS/AOM/CCE 之前，必须先让 DPOMAgent 自身可运营（可探测、可度量、可关联、可优雅退出）。

## What Changes

- 引入 Spring Boot Actuator + Micrometer，提供标准 `/actuator/health/liveness` 与 `/actuator/health/readiness`；仅暴露 `health` 与 `prometheus` 端点（不暴露通用 `/actuator/metrics`）。保留现有 `/api/v1/health` 的返回结构（status/db/executor/external）不变。
- 新增指标：调查提交数、完成/失败/INCONCLUSIVE/拒绝数（终态 counter 单次进程执行路径恰一次，best-effort observability）、执行延迟（每次执行尝试恰好记录一次）、执行器队列深度/活跃线程/容量、reconciliation 实际恢复数、外部适配器（DeepSeek/Drain3/CodeGraphContext）调用延迟。
- 标签契约统一为 `status`/`resultType`/`adapter`/`errorCode` 四项低基数标签；适配器计时只用 `adapter` + `errorCode`（成功 errorCode=NONE、超时=TIMEOUT、其余用有限白名单稳定错误码），不引入 `result` 标签；禁止 investigationId/runId/tenant/serviceCode/日志模板/异常文本作为 label。
- 每个 HTTP 请求生成或接受受限格式 correlationId（`X-Correlation-Id`），所有成功/错误响应（含 413）均回显该头；异步执行传播 correlationId；结构化日志包含 correlationId + investigationId（可用时）+ 稳定 errorCode，且不含密钥、原始日志、请求体、异常 message/堆栈。
- readiness 由 DB、executor 容量与必要内部依赖派生；外部 DeepSeek/Drain3/CGC 为**被动观测**（来自最近一次真实业务调用结果 + 时间戳，超时过期归 UNKNOWN），health endpoint 不主动调用外部、不产生费用/流量/副作用；外部瞬时不可用不使 liveness DOWN。
- 支持优雅停机：复用现有 `investigationExecutor` 的 Spring 生命周期配置（wait-for-tasks/await-termination）+ `server.shutdown=graceful`，停止接收新调查、有界等待在途任务、超时后按现有 reconciliation 语义恢复，禁止无限等待、禁止 double shutdown。
- 测试锁定：指标增量与单次执行路径不重复（best-effort）、低基数标签（key 白名单 + value 有限枚举）、被动健康与过期语义、correlationId 校验/回显/异步传播、readiness/liveness 与 503/200 语义、优雅停机边界、日志无泄漏、`mvn clean verify`。

## Capabilities

### New Capabilities

- `investigation-operability`: 单实例服务的可观测性与生命周期治理（健康探针、指标、关联标识、优雅停机）。

### Modified Capabilities

（无——不修改既有 `investigation-service-api`、`investigation-agent`、`log-evidence-code-context`、`real-diagnostic-regression` 的 spec 级行为。）

## Impact

- **代码（仅 agent-web）**：健康探针/健康指示器、指标注册（业务/执行器/reconciliation/适配器装饰器）、correlationId filter（先于 RequestSizeLimitFilter）、优雅停机配置（executor 生命周期参数）、Actuator 暴露配置。
- **不改动**：`agent-core`（reconciler 位于 agent-web，埋点在 agent-web；core 依赖方向不变）、`agent-adapter-llm`/`agent-adapter-codegraph`/`agent-adapter-runtime`（适配器计时用 agent-web `@Primary` 装饰器包装，不改适配器实现）。
- **依赖**：新增 `spring-boot-starter-actuator` 与 `micrometer-registry-prometheus`（仅暴露 `/actuator/health` 与 `/actuator/prometheus`，不新增常驻控制面/其他服务）。
- **配置**：`application.yml`（management endpoints=health,prometheus、health group 组成与 HTTP 映射、server.shutdown=graceful、executor wait/await 参数、correlationId 头、被动健康过期窗口、指标前缀）。
- **兼容性**：`/api/v1/health` 返回结构不变；`/api/v1/investigations` 请求/响应契约不变（新增可选 `X-Correlation-Id` 请求头，缺省自动生成并在响应回显）。
