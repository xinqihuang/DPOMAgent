# Topic contract: `dpom.diagnosis-event.v2`

- Contract version: `2.0`
- Value schema: `contracts/diagnosis-event/v2/diagnosis-event.schema.json`
- Record key: UTF-8 `investigationId`（必须与 value 完全一致，最大 128 bytes）
- Partitioning: producer 的显式 key partitioning；同一 investigation 的所有 event 必须进入同一 partition
- Delivery: at-least-once；不声明 Kafka exactly-once、XA 或跨数据库事务

## Required headers

| Header | Bound | Meaning |
|---|---:|---|
| `dpom-contract` | 32 bytes | 固定 `diagnosis-event` |
| `dpom-schema-version` | 16 bytes | 与 value `schemaVersion` 一致 |
| `dpom-event-id` | 36 bytes | 与 value `eventId` 一致 |
| `dpom-authority-epoch` | 128 bytes | 与 value authority epoch 一致 |
| `dpom-canonical-sha256` | 64 bytes | value RFC 8785 canonical bytes 的小写 SHA-256 |
| `content-type` | 64 bytes | 固定 `application/json` |

Header、topic、partition、offset、timestamp、producer attempt 与 broker metadata 不进入 canonical value 或 digest。
未知/重复 header、header/value 不一致、非 UTF-8 或越界 header 均持久隔离。

## Bounds, compatibility and retention

- record value 最大 64 KiB；key + headers 总和最大 2 KiB。
- 只接受 schema `2.x`；minor 只允许向后兼容可选字段，未知 major 隔离为 `UNSUPPORTED_SCHEMA`。
- v1 不投递到本 topic；DPOMAgent v1 只走有期限的 compatibility HTTP adapter。
- 建议 broker retention 7 天、cleanup policy `delete`；调查/诊断权威事实保存在 DPOMAgent，SRE 只保存摄取与评估投影，不依赖 broker 永久保存。
- topic ACL：DPOMAgent producer-only，SRE consumer-only；DPOMBase、Portal 和 DeepEval 无访问权。

## Retry and quarantine

- Producer 只从持久化 publication intent 重试，复用相同 key、eventId、intentId 和 canonical bytes。
- 可重试 broker 错误使用有界指数退避、最大次数/年龄/积压；永久失败保留本地终态与审计。
- SRE 只有在本地事务 durable commit 后 acknowledge。重启前未 ack 的 redelivery 由 identity + digest 幂等化。
- 无效 schema、安全边界、digest、authority、sequence 或 identity conflict 写入 SRE durable quarantine 后 ack；
  短暂数据库失败不 ack、不声称接受。Phase 1B 不使用 broker DLT 作为权威隔离库。
