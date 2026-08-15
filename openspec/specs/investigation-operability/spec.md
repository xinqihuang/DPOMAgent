# investigation-operability Specification

## Purpose
让单实例 DPOMAgent 服务可运营：提供标准健康探针、低基数业务与资源指标、请求关联标识与优雅停机，以便在研发环境可靠探测、度量、排障与维护。

## Requirements

### Requirement: Health and readiness endpoints
系统 SHALL 通过 Spring Boot Actuator 暴露标准 liveness 与 readiness 探针，并保留现有 `/api/v1/health` 的返回结构（status/db/executor/external）兼容语义。

#### Scenario: Liveness probe
- **WHEN** 请求 `/actuator/health/liveness`
- **THEN** 进程存活时 SHALL 返回 200 + UP，且该 group 仅含 livenessState，不因外部 DeepSeek/Drain3/CGC 瞬时不可用而 DOWN

#### Scenario: Readiness probe
- **WHEN** 请求 `/actuator/health/readiness`
- **THEN** readiness group SHALL 由 readinessState + DB 检查 + executor 容量组成；UP 时返回 200，DOWN/OUT_OF_SERVICE 时返回 503；外部被动健康指示器 MUST NOT 参与该 group

#### Scenario: Actuator endpoint exposure
- **WHEN** 访问 Actuator 端点
- **THEN** SHALL 仅暴露 `/actuator/health`（含 liveness/readiness）与 `/actuator/prometheus`；MUST NOT 暴露通用 `/actuator/metrics`

#### Scenario: Legacy health compatibility
- **WHEN** 请求 `/api/v1/health`
- **THEN** SHALL 返回与既有结构兼容的 status/db/executor/external 字段

### Requirement: Investigation metrics
系统 SHALL 通过 Micrometer 暴露调查提交数、终态数与执行延迟。指标为 best-effort observability：DB 状态是持久化 truth，指标只在单次进程执行路径下不重复计数；MUST NOT 声称跨进程/跨崩溃严格 exactly-once。

#### Scenario: Submit counter
- **WHEN** 提交调查
- **THEN** 提交数计数器 SHALL 递增

#### Scenario: Terminal counter once per execution path
- **WHEN** 某次执行路径首次观察或产生 COMPLETED/INCONCLUSIVE/FAILED/REJECTED 终态
- **THEN** 终态计数器 SHALL 以 status 与 resultType 为标签递增一次；幂等重放（不派发执行）与重复 reconciliation MUST NOT 重复计数

#### Scenario: Execution latency timer
- **WHEN** 一次调查执行尝试结束（含 COMPLETED/INCONCLUSIVE/FAILED/WAITING_FOR_HUMAN/异常）
- **THEN** 执行延迟 SHALL 恰好记录一次为计时器，标签仅限 status/resultType/errorCode

### Requirement: Executor and reconciliation metrics
系统 SHALL 暴露有界执行器的队列深度、活跃线程、容量，以及 reconciliation 实际恢复的 investigation 数。

#### Scenario: Executor gauges
- **WHEN** 查询指标端点
- **THEN** SHALL 返回执行器 queue.size/active/pool.size/queue.capacity 的实时值

#### Scenario: Reconciliation recovered counter
- **WHEN** 启动 reconciliation 把遗留非终态调查标记为可恢复终态
- **THEN** 计数器 SHALL 计实际被恢复的 investigation 数；空扫描或重复运行 MUST NOT 递增

### Requirement: Adapter metrics
系统 SHALL 记录外部适配器（DeepSeek/Drain3/CodeGraphContext）调用的延迟，标签统一为 `adapter` + `errorCode`。

#### Scenario: Adapter call timer
- **WHEN** 调用外部适配器
- **THEN** SHALL 记录以 adapter 与 errorCode 为标签的计时器（成功 errorCode=NONE、超时=TIMEOUT、其余为有限白名单稳定错误码），MUST NOT 使用 result 标签，且不记录调用参数或响应内容

### Requirement: Low-cardinality metric labels
自定义 `dpom.*` 指标的标签 SHALL 只允许 `status`、`resultType`、`adapter`、`errorCode` 四类，且每类取值 MUST 来自有限枚举；MUST NOT 使用 investigationId、runId、tenant、serviceCode、日志模板或异常文本作为 label。

#### Scenario: Forbidden labels absent
- **WHEN** 任一 `dpom.*` 指标被注册或上报
- **THEN** 其 tag key SHALL 属于明确白名单，tag value SHALL 来自对应有限枚举，且 MUST NOT 含 investigationId/runId/tenant/serviceCode/日志模板/异常文本

### Requirement: Correlation ID
系统 SHALL 为每个 HTTP 请求生成或接受受限格式 correlationId，并在所有响应回显、在异步执行与结构化日志中传播；日志 SHALL 含 correlationId + investigationId（可用时）+ 稳定 errorCode，且 MUST NOT 记录密钥、原始日志、请求体、异常 message/堆栈。

#### Scenario: Generate or accept correlationId
- **WHEN** 请求携带 `X-Correlation-Id`
- **THEN** 合法（受限字符集与长度）时 SHALL 采用并回显；缺省时 SHALL 生成受限格式 correlationId 并回显；非法时 SHALL 返回统一 ErrorResponse 400，并以新生成的安全 correlationId 作为响应头

#### Scenario: CorrelationId on every response
- **WHEN** 任意 API 成功或错误响应（含 413 PAYLOAD_TOO_LARGE）
- **THEN** 响应 SHALL 携带 `X-Correlation-Id` 头；CorrelationIdFilter MUST 先于 RequestSizeLimitFilter 执行

#### Scenario: Async propagation and structured logging
- **WHEN** 异步执行调查并产生日志
- **THEN** 日志 SHALL 携带同一 correlationId 与 investigationId（可用时），且不含密钥/原始日志/请求体/异常 message/堆栈

### Requirement: Passive adapter component status
外部 DeepSeek/Drain3/CGC 的组件状态 SHALL 为被动观测：来自最近一次真实业务调用结果与时间戳；从未调用 SHALL 为 UNKNOWN；状态 MUST 有过期语义；health endpoint MUST NOT 主动调用外部适配器，MUST NOT 暴露 URL、异常文本、响应或凭据。

#### Scenario: Never-called adapter
- **WHEN** 某外部适配器从未被业务调用
- **THEN** 其组件状态 SHALL 为 UNKNOWN

#### Scenario: Stale adapter status
- **WHEN** 最近一次业务调用结果超过过期窗口
- **THEN** 其组件状态 SHALL 归为 UNKNOWN

#### Scenario: No active probe and no leak
- **WHEN** health endpoint 被请求
- **THEN** SHALL NOT 主动调用外部适配器（不产生费用/流量/副作用），且组件详情 MUST NOT 含 URL/异常文本/响应/凭据

### Requirement: Graceful shutdown
系统 SHALL 支持优雅停机：停止接收新调查、有界等待在途任务完成、超时后按现有 reconciliation 语义恢复；MUST NOT 无限等待，MUST NOT double shutdown。

#### Scenario: Bounded drain
- **WHEN** 触发停机
- **THEN** SHALL 停止派发新调查，并在配置超时内等待在途任务；超时后遗留非终态调查 SHALL 由启动 reconciliation 恢复为可恢复终态

#### Scenario: Single shutdown owner
- **WHEN** Spring 生命周期触发 executor 关闭
- **THEN** 关闭 MUST 由单一 owner 完成（executor 生命周期参数 + server.shutdown=graceful），重复关闭 MUST NOT 抛异常或二次等待
