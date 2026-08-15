# Tasks: Add Log Evidence to Code Context

- [x] **T101 — Log evidence domain contract**
  - Tests first: LogEvidence、LogTemplateSummary、ParameterDistribution、EvidenceProvenance 的约束与序列化。
  - Acceptance: DTO 不包含远端 MCP DTO；支持 service/environment/release/commit/timeRange/truncation。

- [x] **T102 — Bounded intake, prefix split and redaction**（依赖：T101）
  - Tests first: 行数/字节上限、结构化前缀分离、Authorization/token/password 脱敏、稳定 hash。
  - Acceptance: 未截断海量日志和原始敏感值不得进入持久化或 LLM 上下文。

- [x] **T103 — Drain3 aggregation**（依赖：T101,T102）
  - Tests first: 同模板聚合、时间范围、severity、代表样本、参数分布、截断 metadata。
  - Acceptance: `McpLogTemplateMinerClient` 输出被转换为 LogEvidence；不得直接把原始结果拼接进 prompt。

- [x] **T104 — Code anchor extraction**（依赖：T103）
  - Tests first: exception、stack frame、logger/class/method、日志常量、HTTP path、mapper id；误匹配和空锚点。
  - Acceptance: 每个 anchor 有 sourceEvidenceId/confidence/ruleVersion；无 RAG/Embedding。

- [x] **T105 — Version-bound graph and source resolution**（依赖：T104）
  - Tests first: READY、NOT_READY、VERSION_MISMATCH、graph unavailable、workspace fallback。
  - Acceptance: CGC 只导航；事实源码绑定 Incident commit，并记录文件/行号。

- [x] **T106 — Evidence Bundle and investigation integration**（依赖：T103,T105）
  - Tests first: provenance、预算排序、truncation、contradiction、degradation；无源码证据不得 ROOT_CAUSE_FOUND。
  - Acceptance: 调查循环消费 Evidence Bundle，结论引用日志与源码 Observation。

- [x] **T107 — Audit and persistence**（依赖：T106）
  - Tests first: LogEvidence/anchor/bundle metadata 的持久化与恢复；不保存巨量原始日志。
  - Acceptance: 工具调用、降级、版本、规则版本和证据引用可从 timeline 审计。

- [x] **T108 — Evaluation fixtures E01/E03/E05**（依赖：T106）
  - 创建 `evals/cases/E01-device-transaction-rollback`、`E03-telemetry-partial-batch-loss`、`E05-downstream-timeout-retry-storm`。
  - 每个包含 incident.json、logs、expected.json、release/commit 和 README。
  - Acceptance: 默认离线评测断言 rootCauseId、expectedSymbols、requiredEvidenceTypes、forbiddenConclusions。

- [x] **T109 — Real Drain3 integration E2E**（依赖：T103,T108）
  - Acceptance: 真实 Drain3 把变量不同的相似日志聚为同一模板并抽取参数；显式开关运行，默认构建跳过。

- [x] **T110 — Real combined E2E**（依赖：T105,T106,T108,T109）
  - Acceptance: 至少 E01 跑通真实 Drain3 → CodeGraphContext → Snapshot source → LLM → evidence-backed conclusion。
  - 记录 model/prompt/toolset/rule/miner 版本、tool calls、token（可得时）、latency 和最终结果。

- [x] **T111 — Boundary and regression acceptance**（依赖：T101-T110）
  - `mvn clean verify`；默认环境无外部服务仍通过。
  - 验证 No RAG/Embedding/Vector DB、无 execute_shell、无生产自动执行、单实例 Java Web。
  - 输出本 Change 的 acceptance report；真实 E2E 未执行时不得归档。
