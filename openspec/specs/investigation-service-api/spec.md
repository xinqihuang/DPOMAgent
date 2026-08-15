# investigation-service-api Specification

## Purpose
TBD - created by archiving change add-investigation-service-api. Update Purpose after archive.

## Requirements

### Requirement: Versioned Investigation API
系统 SHALL 提供 /api/v1/investigations 的提交与查询 API。
#### Scenario: Submit and query
GIVEN 合法请求
WHEN POST /api/v1/investigations
THEN SHALL 返回 202 + investigationId + 状态
AND GET .../steps、.../evidence、.../conclusion 可查询。

### Requirement: Application Orchestration
Controller SHALL NOT 直接拼装依赖；应用层 MUST 完成输入校验、LogEvidenceService、Bundle 持久化、coordinator 调用。
#### Scenario: Bundle before LLM
WHEN 执行调查
THEN EvidenceBundle SHALL 在 LLM 推理前生成并持久化
AND rootCauseId SHALL 来自真实 Conclusion。

### Requirement: Bounded Async Execution
POST SHALL 默认有界异步，返回 202；MUST 使用有界 executor 与拒绝策略，队列满返回 429/503。
#### Scenario: Queue full
WHEN 执行队列已满
THEN SHALL 返回 429 或 503，不得静默丢弃
AND SHALL 事务化补偿：调查标记 FAILED + 稳定 REJECTED 结论 + api_request REJECTED，不留 CREATED/RUNNING 孤儿。

### Requirement: Idempotency
同一 idempotencyKey + 相同 payload SHALL 返回同一 investigationId；相同 key + 不同 payload SHALL 返回 409。
#### Scenario: Replay
WHEN 重复提交相同 key+payload
THEN SHALL 返回原 investigationId。
#### Scenario: Concurrent same key
WHEN 并发同 key 提交
THEN DB 唯一约束 SHALL 作为最终仲裁：同 payload 返回同一 id，异 payload 稳定返回 409。

### Requirement: Status and Error Semantics
非法 400；不存在 404；幂等冲突 409；容量拒绝 429/503；内部失败落 FAILED/INCONCLUSIVE。
#### Scenario: Conclusion not ready
WHEN 查询尚未生成的结论
THEN SHALL NOT 返回 404，而返回 200 + availability/status 或 409/425。
#### Scenario: Evidence not ready
WHEN 查询尚未生成的证据束
THEN SHALL NOT 返回 404（区别于调查不存在），而返回 200 + available=false。

### Requirement: Input Safety and Bounds
logs SHALL 在 Drain3/持久化/LLM 前 redaction；请求体/行数/单行/总字节/timeRange 有界；serviceCode 等校验格式。
#### Scenario: Oversized logs
WHEN logs 超限
THEN SHALL 明确拒绝或按 spec 截断并标记 truncated。

### Requirement: Request Body Size Limit
POST /api/v1/investigations SHALL 在 JSON 反序列化/Controller 前执行可配置总 body 上限（默认 1.1 MiB），超限返回 413 + PAYLOAD_TOO_LARGE（统一 ErrorResponse）；GET 不受影响；不得把 body 写磁盘或日志。
#### Scenario: Content-Length over limit
WHEN 请求 Content-Length 超限
THEN SHALL 返回 413 + code=PAYLOAD_TOO_LARGE。
#### Scenario: Chunked over limit
WHEN 无 Content-Length/chunked 流式 body 超限
THEN SHALL 返回 413 + code=PAYLOAD_TOO_LARGE（有界流读取，不依赖 Content-Length/max-swallow-size）。

### Requirement: Log Safety
应用日志 SHALL NOT 记录 Throwable、异常 message、请求内容、原始日志、密钥、SQL、绝对路径或堆栈；SHALL 仅记录 investigationId（可用时）与稳定 errorCode（白名单类别）。
#### Scenario: Exception logging
WHEN 异常发生
THEN 日志 SHALL 仅含 investigationId + 稳定 errorCode，不含异常 message/堆栈/secret。

### Requirement: DTO Separation and Audit
API DTO SHALL 与领域对象分离；EvidenceBundle/Conclusion/Steps SHALL 可审计；idempotency/execution 元数据 SHALL 持久化。
#### Scenario: Audit query
WHEN 查询时间线/证据/结论
THEN SHALL 返回带 provenance 与版本的审计视图。
#### Scenario: Execution metadata
WHEN 调查执行各阶段
THEN api_request SHALL 事务化更新 RUNNING/COMPLETED/INCONCLUSIVE/FAILED/REJECTED
AND SHALL 记录 started_at/completed_at/last_error_code（不得含原始异常/secret）。

### Requirement: Operability
系统 SHALL 提供健康/就绪信息（DB、executor 容量）；外部能力可 UNKNOWN/NOT_CONFIGURED 但不得伪造 UP。
#### Scenario: Health
WHEN 请求 /api/v1/health
THEN SHALL 返回 DB 与执行器容量状态。
