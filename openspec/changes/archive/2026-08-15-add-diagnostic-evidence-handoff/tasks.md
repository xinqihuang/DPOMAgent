# Tasks

- [x] **T201 — Escalation decision, dual-zone boundary and profile assembly**
  - 测试先行：置信度阈值、未解决矛盾、缺失证据、需要代码级证明；eligible 不触发上传。
  - 交付确定性 `EscalationDecision`（eligible/reasons/missingEvidence/confidence）。
  - Profile 用 Spring 条件装配隔离接口：production 只暴露升级/打包/审批/上传，development 只暴露 verify/import。
  - 非法/未知 mode 启动失败（不静默降级）；production 无源码诊断复用 EvidenceBundle + LogRedactor。
- [x] **T202 — Versioned redacted evidence package**
  - 测试先行：确定性 manifest、SHA-256 校验、大小/条数上限、固定路径、内容 allow-list 与禁止字段拒绝。
  - 复用 EvidenceBundle/LogRedactor；仅允许有界 CodeGraph 摘要（非源码）；序列化确定性。
- [x] **T203 — OBS adapter, approval gate and store boundary**
  - 测试先行：默认关闭、allow-list bucket/prefix、服务生成对象名、未批准拒绝、adapter 不存在 fail closed。
  - 正式 Profile 禁止把 InMemoryEvidenceHandoffStore 当 OBS；真实 adapter 不存在时返回 OBS_ADAPTER_UNAVAILABLE。
  - 审批与上传分离：approveUpload/rejectUpload 绑定 packageId 并记录 approverRef/reason/过期；
    upload 只读 APPROVED，不接受 approval 布尔；上传失败不写 uploadedAt/objectKey。
  - 不引入 OBS SDK；单元测试只用 fake/in-memory adapter（测试专用配置注入）。
- [x] **T204 — Development-side verification, idempotent import and audit**
  - 测试先行：schema 不支持、checksum 错误、超限、禁止字段、release/commit/service 不匹配 → fail closed。
  - 校验通过恢复为现有 EvidenceBundle/调查输入；并发导入以唯一键幂等仲裁，不暴露 DuplicateKeyException。
  - 追加式审计（escalation/package-build/approval/rejection/upload/verify/import 成功与失败，best-effort）。
  - fake-store 端到端 acceptance；`mvn clean verify` + `openspec validate --strict`。
  - 真实 OBS acceptance 保持禁用，直到配置批准 bucket 且显式批准一次上传。
