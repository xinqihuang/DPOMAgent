# Diagnosis Event Contract v2

Diagnosis Event v2 是 DPOMAgent 到 SRE Intelligence 的 transport-neutral 权威诊断事实；v1 资产保持不变，
只用于 DPOMAgent 兼容窗口。

## Canonical identity

- 对完整事件应用 RFC 8785 JCS，再对 UTF-8 字节计算小写十六进制 SHA-256。
- canonical event 不包含 HTTP/Kafka header、签名、topic、partition、offset、consumer group 或 retry metadata。
- 首次发布、broker 重投和人工 replay 必须复用 `eventId`、`idempotencyKey`、`publicationIntentId` 和 canonical bytes。
- 同 identity + 同 digest 是等价重复；同 identity + 不同 digest 为 `IDEMPOTENCY_CONFLICT`。

## Authority and ordering

- `sourceAuthority` 固定记录 producer service、部署管理的 authority epoch、已提交 aggregateVersion 和不可变
  publicationIntentId。
- SRE 按迁移状态验证 epoch；无效 epoch 为 `AUTHORITY_CONFLICT`，不得覆盖已接受事实。
- `aggregateSequence` 在 investigation 内单调递增；gap 隔离为 `SEQUENCE_GAP`，回退为 `SEQUENCE_REGRESSION`。

## Bounds and security

- canonical event 最大 64 KiB，inline payload 最大 16 KiB，Evidence Manifest reference 最大 2 KiB。
- 禁止凭据、Prompt、原始模型输出、证据正文、provider DTO、broker 字段、任意文件路径和绕过受控 locator 的存储字段。
- Artifact 必须校验 locator、media type、byteSize、SHA-256、schema version 和 retention class。

正样例的 canonical digest 存在 `fixtures/manifest.json`，不写入事件本身以避免自引用摘要。
