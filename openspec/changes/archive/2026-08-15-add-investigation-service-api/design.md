# Design: Investigation Service API

## 1. API
POST /api/v1/investigations；GET /api/v1/investigations/{id}；GET .../steps；GET .../evidence；GET .../conclusion。
请求含 serviceCode/environment/release/commit/symptom/timeRange/logs。响应稳定 schema（investigationId/status/createdAt/updatedAt/error.code），不含 secret/stacktrace/未脱敏日志。

## 2. Application Layer
InvestigationApplicationService 编排：Incident/Investigation 创建 → 输入校验 → LogEvidenceService → EvidenceBundle 持久化 → 有界异步 coordinator.run → 状态/结论查询。rootCauseId 来自真实 Conclusion。

## 3. Execution Model
有界 ThreadPoolTaskExecutor（core/max/queue/caller-runs 或 Abort），队列满 429/503；幂等键持久化；启动 reconciliation 把 RUNNING 遗留任务置为可恢复/FAILED。

## 4. Persistence
V6 migration：investigation_api_request（idempotency_key/payload_hash/investigation_id/status）与执行元数据。

## 5. Security
logs 先 redaction；请求体/行数/单行/总字节/timeRange 有界；serviceCode 等格式校验；禁止任意路径/URL/命令/脚本。

## 6. Operability
GET /api/v1/health：DB up、executor 容量；外部能力 UNKNOWN/NOT_CONFIGURED。日志带 investigationId/runId，不含 secret。
