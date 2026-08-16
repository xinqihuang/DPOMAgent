# add-diagnostic-evidence-handoff 验收报告（整改后）

日期：2026-08-15
状态：完成并已归档（2026-08-15，Change 4/4 task）

## 1. 架构基线

DPOMAgent 是「同一套诊断引擎、两种部署 Profile、双区域闭环」：

- 生产区域（production profile）：升级判定、打包、审批、审批后上传；不要求源码，只允许版本匹配、边界裁剪的
  CodeGraph。
- 研发区域（development profile）：下载、校验、导入/恢复；结合准确源码做最终 RCA。
- 跨区域交接：OBS 是受控传输通道；escalationEligible 与上传批准分离；只上传版本化、限量、脱敏的
  Diagnostic Evidence Package。

## 2. 三个 P1 与两个 P2 的修复证据

### P1-1 Profile 隔离（Spring 条件装配）
- `HandoffProfile` 枚举 + `HandoffConfig.profile`；`HandoffProfile.from(mode)` 对非法值抛异常 → 启动失败。
- `ProductionHandoffController`（`@ConditionalOnProperty(mode=production)`）只暴露 escalation/package/approve/reject/upload。
- `DevelopmentHandoffController`（`@ConditionalOnProperty(mode=development, matchIfMissing=true)`）只暴露 verify/import。
- 证据：`ProductionProfileTest`（3）、`DevelopmentProfileTest`（5）、`UnknownModeFailsStartupTest`（1）。

### P1-2 禁止生产内存假 OBS
- `HandoffConfiguration.evidenceHandoffStore()` 固定返回 `DisabledEvidenceHandoffStore`；不再由 `obs.enabled` 装配 InMemory。
- `InMemoryEvidenceHandoffStore` 仅测试源码引用（边界扫描确认无生产 wiring）。
- `requireObsTransport()`：`obs.enabled=false` → OBS_DISABLED；`obs.enabled=true` 但 store 未启用 → OBS_ADAPTER_UNAVAILABLE，
  不写 uploadedAt/objectKey、不返回 objectKey。
- 证据：`EvidenceHandoffServiceTest.uploadFailsClosedWhenAdapterUnavailable` 等。

### P1-3 审批与上传分离
- `approveUpload(investigationId, packageId, approverRef, reason)` / `rejectUpload(...)` 独立持久化。
- `upload(investigationId, packageId)` 只读数据库 APPROVED 且未过期，不接受 approval 布尔（DTO 无该字段）。
- 审批绑定具体 packageId；新包不继承旧包审批；记录 approverRef/reason/approvedAt/approval_expires_at。
- 上传失败保留 APPROVED 审计状态，不写 uploadedAt/objectKey，记 UPLOAD FAILURE 审计。
- 证据：`EvidenceHandoffServiceTest` 15 用例（未批准/拒绝/过期/其他 packageId/失败不标记成功/approverRef 必填）。

### P2-1 并发幂等导入（含冲突后核对）
- `handoff_import.package_id` 唯一约束为最终仲裁；`verifyAndImport` 捕获 DataIntegrityViolationException 后**重读并核对**已有记录：
  仅当已有记录的 service/release/commit 与本次包一致才返回 `alreadyImported=true`，不暴露 DuplicateKeyException。
- 无记录（非唯一键完整性异常）→ 抛 `PACKAGE_INVALID`，不得伪装为幂等成功；已有记录版本不一致 → 抛 `VERSION_MISMATCH`。
- 证据：`HandoffImportConcurrencyTest`（真实 H2 两线程并发）、`importIsIdempotentOnDuplicateKeyWithMatchingIdentity`、
  `nonUniqueIntegrityViolationIsNotIdempotent`、`versionMismatchOnExistingImportFails`。

### P2-2 追加式审计
- `handoff_audit` 表记录 eventType/result/errorCode/investigationId/packageId/correlationId/timestamp，无证据正文/敏感值。
- escalation/package-build/approval/rejection/upload/verify/import 成功与失败均审计；写失败 best-effort。
- 证据：`HandoffAuditTest`（成功全链路计数 + UPLOAD FAILURE 计数）、
  `EvidenceHandoffServiceTest.auditWriteFailureDoesNotChangeBusinessResult`。

## 3. 测试数量

`mvn clean verify`：BUILD SUCCESS，共 246 个测试，0 失败，0 错误，6 跳过。

- agent-adapter：19（llm 8、runtime 6、codegraph 5）
- agent-core：101（1 跳过）
- agent-web：126（5 跳过）

本 Change 相关测试 51 个：
- agent-core/handoff：Builder 7 + Parser 1 + EscalationEvaluator 6 + Service 15 + Serializer 2 + Verifier 7 = 38。
- agent-web：HandoffE2ETest 1 + HandoffAuditTest 2 + HandoffImportConcurrencyTest 1 + ProductionProfileTest 3 +
  DevelopmentProfileTest 5 + UnknownModeFailsStartupTest 1 = 13。

## 4. 失败/跳过

- 失败/错误：0。
- 跳过 6（均为既有外部依赖 E2E）：CombinedE2ETest、DiagnosticRegressionE2ETest、Drain3IntegrationE2ETest、
  InvestigationApiRealE2ETest、Log4jStacktraceE2ETest、CodeWorkspaceTest(1 项)。本 Change 测试全部执行。

## 5. 边界核对

- No RAG/Embedding/Vector DB：grep 0 命中。
- No arbitrary shell：主代码 0 命中（仅 InMemory 类定义与测试引用）。
- No OBS SDK：0 命中；真实 OBS 集成未做。
- No production InMemory store：InMemoryEvidenceHandoffStore 仅在测试源码引用，HandoffConfiguration 不装配它。
- No source/credential upload：证据包 allow-list + LogRedactor + ForbiddenContentScanner 双层防护。
- No automatic production execution：无新增写工具/执行入口；上传仅经独立审批。
- 单实例 Java Web：保持；未新增基础设施。

## 6. 未完成项 / 风险

- 正式 Profile 未实现真实 OBS：`obs.enabled=true` 且无真实 adapter 时 fail closed（OBS_ADAPTER_UNAVAILABLE），
  下载/上传均不可用于正式环境（留待下一 Change）。
- 证据包字节暂存于进程内 `packageCache`（构建→上传同进程），重启后不可恢复。
- 禁止字段扫描是模式匹配，非语义解析，存在绕过风险。
- OBS allow-list 建模为单一 bucket/prefix；多值白名单需扩展。
- alarm/timeline/topology/metrics section 当前由观测数据映射留空。

## 7. 下一 Change 建议

1. `add-obs-transport-adapter`：基于端口接入真实 OBS（最小权限 V4 签名或官方 SDK），完成 allow-list/服务端加密/
   生命周期/checksum 上传与审计，配真实 OBS E2E。
2. `add-dev-side-evidence-import`：研发侧把校验通过的证据包落为 Investigation/Incident 并绑定快照，走既有状态机 RCA。
3. `add-runtime-evidence-enrichment`：生产运行时证据确定性回填 alarm/timeline/topology/metrics。

## 8. 验收命令

- `openspec validate add-diagnostic-evidence-handoff --strict` → valid，4/4 complete。
- `mvn clean verify` → BUILD SUCCESS（246 tests, 0 failures, 6 skipped）。
- `mvn clean compile`（test-fixtures/energy-platform-demo）→ BUILD SUCCESS。
- 环境：JDK 21（C:\Program Files\Java\jdk-21.0.11）+ Maven 3.9.16（C:\apache-maven-3.9.16），命令中显式指定。
