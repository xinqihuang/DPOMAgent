# add-investigation-service-api 验收报告

日期：2026-08-15
Change：add-investigation-service-api（未归档，等待独立验收）

## API（/api/v1）
- POST /api/v1/investigations：提交调查（202 + investigationId + status）
- GET /api/v1/investigations/{id}：查询摘要与状态
- GET /api/v1/investigations/{id}/steps：时间线/步骤（StepResponse DTO）
- GET /api/v1/investigations/{id}/evidence：证据审计视图（available=true + LogEvidenceResponse/CodeAnchorResponse/CodeEvidenceResponse，含 provenance；未生成时 available=false 非 404）
- GET /api/v1/investigations/{id}/conclusion：结论（未生成时 available=false，非 404）
- GET /api/v1/health：整体状态由 DB + executor 容量推导；外部能力 UNKNOWN/NOT_CONFIGURED

## 应用编排层
InvestigationApplicationService：输入校验 → 事务化创建（Incident/Investigation/api_request 同事务，idempotency_key 唯一约束为并发仲裁）→ 提交后派发有界异步执行 → 拒绝时事务化补偿（FAILED + REJECTED 结论 + api_request REJECTED，不留 CREATED/RUNNING 孤儿）。执行阶段同步 api_request（RUNNING/COMPLETED/INCONCLUSIVE/FAILED）+ started_at/completed_at/last_error_code（V7 migration，不改 V1–V6）。rootCauseId 来自真实 Conclusion；产品主路径（agent-web + 调查运行时）不读 evals/expected.json（grep 验证：agent-web main 0 处、core 仅隔离在 com.dpom.agent.core.eval 包且不被运行时引用）。

## 执行模型
有界 ThreadPoolTaskExecutor（core2/max4/queue10，AbortPolicy）；队列满 503 + CAPACITY_FULL；幂等键持久化（V6 migration investigation_api_request + V7 执行元数据）；同 key 同 payload 返回同 id、同 key 异 payload 409 + IDEMPOTENCY_CONFLICT（含 investigationId）；启动 reconciliation 把非终态调查标记 FAILED，结论恰一次、api_request 同步 FAILED，重复调用幂等。

## 统一错误契约
GlobalExceptionHandler（@RestControllerAdvice 继承 ResponseEntityExceptionHandler）返回稳定 code/message/investigationId；不泄漏 Java 类名/堆栈/SQL/路径/secret。400 BAD_REQUEST / 404 NOT_FOUND / 405 METHOD_NOT_ALLOWED / 409 IDEMPOTENCY_CONFLICT / 413 PAYLOAD_TOO_LARGE / 503 CAPACITY_FULL / 500 INTERNAL_ERROR。

## 日志安全（T309）
InvestigationApplicationService 与 GlobalExceptionHandler 改为结构化最小化日志：只记录 investigationId（可用时）与稳定 errorCode（白名单类别 INVALID_ARGUMENT/ILLEGAL_STATE/EXECUTION_ERROR/INTERNAL_ERROR），不记录 Throwable、异常 message、请求内容、原始日志、密钥、SQL、绝对路径、堆栈。LogSafetyTest（Logback ListAppender）以含 secret/raw-log/SQL/path sentinel 的异常 message 触发执行失败与 handler，断言日志全文不含这些 sentinel 与堆栈，且 throwableProxy 恒为 null；API 仍返回稳定错误契约。

## 请求体上限（T310）
POST /api/v1/investigations 增加可配置总 body 上限（dpom.api.max-body-bytes，默认 1.1 MiB=1153434），RequestSizeLimitFilter 在 JSON 反序列化/Controller 前拒绝：Content-Length 超限走快速路径，无 Content-Length/chunked 走有界流读取（只读到 max+1 字节即拒绝），返回 413 + ErrorResponse code=PAYLOAD_TOO_LARGE；GET 不受影响；不把 body 写磁盘或日志；不依赖 max-swallow-size。RequestSizeLimitFilterTest 覆盖单元逻辑，RequestSizeLimitHttpTest（RANDOM_PORT 真实 HTTP）覆盖 Content-Length 与流式超限及 GET 不受影响。

## 输入安全
logs 非空、≤1000 行、单行 ≤8192 UTF-8 字节、总量 ≤1MB；timeRange 格式 1m–24h；idempotencyKey `[A-Za-z0-9._-]{1,128}`；symptom 非空、≤512 字符、≤1024 UTF-8 字节、拒绝命令/URL/绝对路径注入标记；serviceCode/environment/release/commit 严格字符集（拒绝路径/URL/命令字符）。

## 测试统计（mvn clean verify）
BUILD SUCCESS，0 failure，0 error，153 测试，6 跳过（adapter 19 + core 63 + web 71）。
跳过：CodeWorkspace symlink（Windows）；Log4jStacktrace/Combined/Drain3/DiagnosticRegression/InvestigationApiReal E2E（真实外部服务默认跳过）。

### 本轮新增/强化测试（9）
- LogSafetyTest：asyncFailureLogsNoSecretRawLogSqlPathOrStacktrace
- GlobalExceptionHandlerTest：genericExceptionDoesNotLogThrowableOrMessage
- RequestSizeLimitFilterTest：rejectsWhenContentLengthExceedsLimit、rejectsStreamedBodyWithoutContentLength、passesThroughWhenBodyWithinLimit、skipsNonInvestigationPost
- RequestSizeLimitHttpTest：postOverContentLengthLimitReturns413、postStreamedBodyOverLimitReturns413、getUnaffectedByBodyLimit

## 真实外部 HTTP E2E（已执行，DEEPSEEK_API_KEY 由本地环境注入）
investigation-api-e2e.json（独立 clean 后原子重跑，timestamp 2026-08-14T18:50:02.011753200Z）：executed=true, passed=true, caseId=E01, investigationId=1, status=COMPLETED, rootCauseId=AssetRepository.insert, model=deepseek-v4-pro。
注：首次运行因未注入 DEEPSEEK_API_KEY 而 401（非代码缺陷），注入后通过；真实 LLM 仍存在非确定性风险。

## 边界审计
No RAG/Embedding/Vector DB；无 arbitrary shell；无自动生产执行；不自动执行诊断/缓解脚本；不连接/修改真实 CCE；单实例 Java Web；无前端 UI；未开发 V2。docs/local-environment.md 保持 gitignored，不读取/不输出其内容。

## 已知风险
真实 LLM 诊断存在非确定性（rootCauseId 偶发偏离）；生产建议配置超时与重试策略并在告警中观察 inconclusive 率。body 上限 1.1 MiB 略高于字段总量，极端 JSON 转义膨胀时可能提前拒绝（可用 dpom.api.max-body-bytes 上调）。

## 结论
本 Change 不归档，停止等待独立验收。
