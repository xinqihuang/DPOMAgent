# Design: dual-zone diagnostic evidence handoff

## Flow

```text
Production Investigation (production profile)
  -> EscalationEvaluator -> EscalationDecision(eligible/reasons/missingEvidence/confidence)
  -> EvidenceHandoffPackage (build, deterministic manifest + checksums)
  -> approveUpload(packageId, approverRef, reason)  [separate persisted decision]
  -> upload(packageId)  [reads APPROVED only, no approval flag]
  -> OBS adapter (disabled by default; no real adapter -> fail closed)
  -> development profile -> verify/import -> recover EvidenceBundle -> source-aware RCA
```

## Profiles and assembly (P1)

`dpom.handoff.mode` 解析为 `HandoffProfile`（production/development），非法值在装配期抛异常导致启动失败。
接口按 Profile 用 `@ConditionalOnProperty` 条件装配两个控制器：

- `ProductionHandoffController`（production）：escalation / package-build / approve / reject / upload。
- `DevelopmentHandoffController`（development，缺省）：verify/import。

服务层（`EvidenceHandoffService`）是同一引擎、不按 mode 做业务分支；隔离只发生在装配层。

## Store boundary (P1)

`EvidenceHandoffStore` 端口默认装配 `DisabledEvidenceHandoffStore`；`InMemoryEvidenceHandoffStore` 只经
测试专用配置（`@TestConfiguration @Primary`）注入，不通过 `obs.enabled` 开关装配。`obs.enabled=true`
但无真实 adapter 时，上传/下载 fail closed 返回 `OBS_ADAPTER_UNAVAILABLE`，绝不返回假成功 objectKey。

## Approval / upload separation (P1)

`handoff_upload` 每行绑定唯一 `package_id`，审批列含 `approval_status/approved_at/approver_ref/
approval_reason/approval_expires_at`。`approveUpload`/`rejectUpload` 独立持久化决定；`upload` 只读
APPROVED 且未过期，不接受 approval 布尔。失败保留 APPROVED 审计状态，不写 uploadedAt/objectKey。

## Verification and import (P2)

下载后按序校验：路径 allow-list → schema → service/release/commit → checksum → 大小/条数 → 禁止字段，
任一失败 fail closed。`handoff_import.package_id` 唯一约束作为并发幂等仲裁：重复插入冲突时重读已有记录
返回 `alreadyImported=true`，不向调用方暴露 DuplicateKeyException。

## Audit (P2)

`handoff_audit` 追加式记录 escalation/package-build/approval/rejection/upload/verify/import 的成功与失败，
字段 eventType/result/errorCode/investigationId/packageId/correlationId/timestamp，不含证据正文或敏感值。
审计写失败 best-effort，不改变业务结果；上传成功状态本身仍事务一致（uploadedAt/objectKey 仅在成功时写入）。
