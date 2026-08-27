# Topic contract: `dpom.diagnosis-progress.v1`

- Contract version: `1.0`
- Value schema: `contracts/diagnosis-progress/v1/diagnosis-progress.schema.json`
- Record key: UTF-8 `investigationId`（最大 128 bytes）
- Partitioning: 与 Diagnosis Event 相同的显式 investigation key，保证 progressSequence 的 partition 内顺序
- Delivery: at-least-once；SSE 与 Kafka 都读取同一个 DPOMAgent persisted authority audit/progress outbox

## Headers and bounds

Required headers: `dpom-contract=diagnosis-progress`、`dpom-schema-version`、`dpom-progress-id`、
`dpom-authority-epoch`、`dpom-canonical-sha256`、`content-type=application/json`。单 header 最大 128 bytes，
全部 header + key 最大 2 KiB；value 最大 8 KiB。

Header 与 broker metadata 不进入 canonical digest。header/value mismatch、unknown major、越界、非 UTF-8、
证据/Prompt/模型输出/凭据/任意异常文本一律隔离。

## Compatibility, retry and retention

- `1.x` minor 遵循只增可选字段；未知 major fail closed。
- producer 重试复用 progressId、progressSequence 和 canonical bytes；consumer 按 investigationId + sequence + digest 幂等。
- 建议 retention 24 小时、cleanup policy `delete`；Portal SSE replay window 与该 retention 独立，读取 DPOMAgent 的受认证有界 API，不跨服务读库。
- 可重试 broker/DB 故障有界重试；安全/契约/顺序冲突写入 SRE durable quarantine 后 ack，不使用 broker DLT 代替审计。
- topic ACL：DPOMAgent producer-only，SRE consumer-only；Portal 通过受认证的 DPOMAgent SSE/REST，不消费 Kafka。
