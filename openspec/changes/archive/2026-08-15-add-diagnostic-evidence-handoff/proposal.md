# Add diagnostic evidence handoff

## Why

生产侧源码不能传到外网；生产环境只能使用华为云运行证据（AOM/CES/APM/LTS/CCE）和经过边界裁剪的
CodeGraph。当这些证据不足以定根因时，生产侧必须把脱敏、可审计的证据包经 OBS 交给研发侧，
由研发侧结合准确版本源码完成最终 RCA。

## What changes

- 明确双区域边界：同一套诊断引擎，`production` 与 `development` 两种部署 Profile 构成闭环，
  接口按 Profile 通过 Spring 条件装配隔离；非法/未知 mode 启动失败。
- 引入确定性升级判定 `EscalationDecision`（eligible/reasons/missingEvidence/confidence）。
- 构建版本化、限量、脱敏的 Diagnostic Evidence Package：确定性 manifest、SHA-256 checksum、
  大小/条数上限、内容 allow-list 与禁止字段（源码/凭据）拒绝。
- 新增 OBS outbound 端口/适配器；正式 Profile 禁止把内存假存储当 OBS，真实 adapter 尚不存在时
  fail closed（`OBS_ADAPTER_UNAVAILABLE`），真实 OBS SDK 集成留待后续 Change。
- 审批与上传彻底分离：`approveUpload`/`rejectUpload` 是绑定具体 packageId、持久化、可审计的决定；
  `upload` 只读数据库既有 APPROVED 状态，不接受 approval 入参。
- 新增研发侧接收/校验 API，fail closed，可恢复为现有 EvidenceBundle；并发导入以数据库唯一键幂等仲裁。
- 补全追加式审计（escalation/package-build/approval/rejection/upload/verify/import 的成功与失败），
  best-effort，不改变业务结果。

## Boundaries

- 复用既有 EvidenceBundle、LogRedactor、调查状态机；不另造平行模型。
- 无源码、凭据、任意路径、任意 shell、RAG、Embedding 或向量库。
- 无自动生产修复、无自动上传；上传失败不得写 uploadedAt/objectKey。
- 审计不含证据正文、凭据、对象内容或敏感值。
