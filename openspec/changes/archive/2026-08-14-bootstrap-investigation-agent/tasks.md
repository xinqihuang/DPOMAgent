# Tasks Index

> 审计时间：2026-08-14。\`[x]\` 表示验收条件已由对应测试验证并通过 \`mvn clean verify\`；判定基于测试与行为，而非文件存在。

- [x] **T001 — Maven Java Web Skeleton**（依赖：无）
  - 验证：\`mvn clean verify\` + \`mvn -pl agent-web -am package\`；仅 agent-web 生成 executable jar；Checkstyle 通过。
- [x] **T002 — MySQL Investigation Schema**（依赖：T001）
  - 验证：\`InvestigationPersistenceTest\` 覆盖空库迁移与 Investigation→Run→Step→Observation→Hypothesis 可恢复。
- [x] **T003 — LLM Adapter & Tool Contract**（依赖：T001）
  - 验证：\`FakeModelClientTest\`（普通回答/tool call/tool result 续跑/超时/错误）+ \`DeepSeekModelClientTest\`（协议映射）。
- [x] **T004 — Investigation State Machine**（依赖：T002,T003）
  - 验证：\`InvestigationCoordinatorTest\` 覆盖完成、budget 截断、等待人工、恢复、否定证据保留。
- [x] **T005 — DPOMCodeGraph Client**（依赖：T001）
  - 验证：\`McpCodeGraphClientTest\` 覆盖 list_indexed_repositories / analyze_code_relationships / find_code 与错误解析。
  - 说明：实际通过 CodeGraphContext 的 MCP 接入（非 DPOMCodeGraphService REST），见 current truth spec。
- [x] **T006 — Controlled Code Workspace**（依赖：T005）
  - 验证：\`CodeWorkspaceTest\` 覆盖 list/search/read、三类 path escape 拒绝、超大源码限制。
- [x] **T007 — Stacktrace Code Investigation**（依赖：T004,T006）
  - 验证：\`StacktraceInvestigatorTest\` 跑通 Controller→Service→Repository fixture，RCA 引用源码位置而非仅 CGC 文本。
- [x] **T008 — DPOMBaseMCP Runtime Evidence**（依赖：T001）
  - 验证：\`DpomBaseMcpClientTest\` 覆盖 success/empty/timeout/error。
- [x] **T009 — Symptom-driven Hypothesis Loop**（依赖：T004,T006,T008）
  - 验证：\`SymptomInvestigationTest\` 多假设、使用 runtime/code 工具、invalidate 错误假设、证据不足进入 WAITING_FOR_HUMAN。
- [x] **T010 — Diagnostic Script Artifact**（依赖：T009）
  - 验证：\`ScriptPolicyValidatorTest\` + \`ScriptArtifactServiceTest\`（安全脚本生成、UPDATE 型 read-only 拒绝、回传结果推进）。
- [x] **T011 — Mitigation Script Artifact**（依赖：T010）
  - 验证：\`MitigationScriptTest\`（MITIGATION + REQUIRES_APPROVAL；无自动执行路径）。
- [x] **T012 — TOP Case: Device Create Not Persisted**（依赖：T009,T010）
  - 验证：\`TopCaseDeviceNotPersistedTest\` 四个 Case（A/B/C/D），结论引用 Observation，RCA 与 expected 对齐。
- [x] **T013 — E2E Regression & Benchmark（最小基准骨架）**（依赖：T007,T012）
  - 验证：\`BenchmarkDatasetTest\`（5 stacktrace + 4 device 共 9 例数据集定义）+ \`docs/benchmark-report.md\` 记录 model/prompt/toolset 版本（占位）。
  - 说明：本轮仅完成最小基准骨架（数据集定义 + 报告占位），不声称真实 E2E Regression & Benchmark 已完整完成。独立 \`evals/\`、独立 fixtures、机器可读指标计算与真实外部链路（真实 LLM/CGC/Drain3）仍属下一阶段；\`Log4jStacktraceE2ETest\` 需 \`DPOM_E2E=true\` 显式启用（默认跳过）。
- [x] **T014 — Log Template Mining (Drain3 MCP 接口与映射)**（依赖：T008）
  - 验证：\`McpLogTemplateMinerClientTest\`（train_logs/list_templates 的映射解析）+ \`mine_log_templates\` 工具 schema 为 lines string array（\`InvestigationToolExecutorTest.mineLogTemplatesLinesIsStringArray\`）。
  - 说明：本轮仅完成 Drain3 MCP 接口与映射测试；真实 Drain3 聚类与参数抽取端到端尚未在默认构建中验证（需真实 Drain3 sidecar）。Drain3 通过 MCP（\`McpLogTemplateMinerClient\`）接入，非 REST sidecar；见 current truth spec。
