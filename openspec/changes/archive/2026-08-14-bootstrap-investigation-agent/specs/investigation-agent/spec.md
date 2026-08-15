# DPOM Investigation Agent Specification

## Purpose
提供研发侧症状驱动故障调查能力。

## Requirements

### Requirement: Single Java Web Application
系统 SHALL 作为研发环境单实例 Java Web 应用运行。
#### Scenario: Start
GIVEN JDK21/MySQL/外部服务配置就绪
WHEN `java -jar agent-web.jar`
THEN SHALL 启动 Spring MVC
AND SHALL NOT 依赖 Docker/K8s/注册中心。

### Requirement: LLM Provider Isolation
系统 SHALL 通过 ModelClient Adapter 隔离具体 LLM Provider。
#### Scenario: Replace Provider
WHEN 更换模型 Provider
THEN Core SHALL 不修改
AND Provider DTO SHALL NOT 泄漏到 Core。

### Requirement: Persistent Investigation
系统 SHALL 持久化 Investigation/Run/Step/Observation/Hypothesis/Conclusion。
#### Scenario: Resume
GIVEN 调查已进行数步
WHEN 应用重启
THEN SHALL 从 MySQL 恢复状态
AND 不依赖原 LLM 会话。

### Requirement: Bounded Investigation
调查 MUST 有 maxSteps/maxToolCalls/maxDuration/maxNoProgressRounds。
#### Scenario: Budget Reached
WHEN 达到任一预算
THEN SHALL 停止自动调查
AND 输出 INSUFFICIENT_EVIDENCE 或 HUMAN_ACTION_REQUIRED。

### Requirement: Symptom Driven Hypotheses
无异常堆栈时 Agent MUST 从业务症状生成候选业务路径和假设。
#### Scenario: Device Create Not Persisted
GIVEN “创建设备成功但 DB 无记录”
WHEN 开始调查
THEN SHALL 考虑请求到达、业务分支、持久化调用、SQL/事务、datasource/schema/tenant、
异步链路、读取侧和发布回归
AND SHALL NOT 无证据直接确认根因。

### Requirement: Hypothesis Status
Hypothesis SHALL 使用 PROPOSED/VALIDATING/VALIDATED/INVALIDATED/INCONCLUSIVE。
#### Scenario: Contradiction
WHEN Observation 与 H1 冲突
THEN H1 SHALL 变为 INVALIDATED
AND 保留否定证据。

### Requirement: Runtime Evidence
Agent SHALL 通过 RuntimeEvidenceClient 调用 DPOMBaseMCPServer。
#### Scenario: Query Runtime
WHEN Agent 需要日志/Trace/指标/告警
THEN Adapter SHALL 返回内部 ArtifactRef/ObservationInput
AND 远端 DTO SHALL NOT 泄漏到 Core。

### Requirement: Code Snapshot
Agent SHALL 根据 serviceCode + commit/release 解析正确 Snapshot。
#### Scenario: Resolve Exact Version
GIVEN Incident 有 serviceCode+commitSha
WHEN 进入代码调查
THEN SHALL 获取 READY snapshotId/workspace
AND 代码证据绑定 commitSha。

### Requirement: Controlled Workspace
Agent SHALL 只在 Snapshot 根目录搜索/读取源码。
#### Scenario: Path Escape
WHEN path 包含 ../、绝对路径逃逸或 symlink escape
THEN SHALL 拒绝。

### Requirement: Code Tools
LLM SHALL 至少可用 list_files/search_text/read_source/find_symbol/find_callers/find_callees/find_call_chain/find_class_hierarchy。
#### Scenario: No Shell Tool
WHEN 枚举 Toolset
THEN MUST NOT 存在 execute_shell。

### Requirement: Graph and Source Separation
CGC 用于导航，源码事实来自 Snapshot/Git。
#### Scenario: Graph Candidate
WHEN CGC 返回候选调用链
THEN Agent SHOULD 读取真实源码
AND MUST NOT 仅凭静态图宣称运行时根因。

### Requirement: Observation-backed Conclusion
关键结论 SHALL 引用 Observation。
#### Scenario: Transaction Rollback
WHEN 结论声称 rollback
THEN SHALL 有日志/Trace/SQL/脚本结果或源码证据支持。

### Requirement: Dynamic Diagnostic Script
证据不足时 Agent SHALL 能生成 Shell/Python/只读 SQL ScriptArtifact。
#### Scenario: Missing DB Evidence
WHEN 无法判断 INSERT/COMMIT
THEN Artifact SHALL 包含 purpose/hypothesesToValidate/language/riskLevel/readOnly/expectedOutput/content
AND SHALL NOT 自动执行。

### Requirement: Diagnostic Script Safety
READ_ONLY_DIAGNOSTIC MUST 通过 ScriptPolicyValidator。
#### Scenario: Mutation Detected
GIVEN 脚本含 rm/kill/restart/UPDATE/DELETE/INSERT/DDL 等修改动作
WHEN 标记 READ_ONLY_DIAGNOSTIC
THEN SHALL 拒绝并要求重新生成或升级为 MITIGATION。

### Requirement: Mitigation Artifact
系统 MAY 生成修复脚本，但 MUST 标记 MITIGATION + REQUIRES_APPROVAL。
#### Scenario: Repair Script
WHEN SRE 请求修复建议
THEN Artifact MUST 包含 preconditions/verification/rollback
AND DPOMAgent MUST NOT 自动执行。

### Requirement: Human Evidence Feedback
系统 SHALL 支持 SRE 提交脚本执行结果并继续调查。
#### Scenario: Submit Result
WHEN SRE 回传 script result
THEN SHALL 新建 ArtifactRef/Observation
AND Investigation 可从 WAITING_FOR_HUMAN 恢复。

### Requirement: Audit
ToolCall/LLM Run/状态变化/脚本/结论 MUST 可审计。
#### Scenario: Timeline
WHEN 查询 Investigation timeline
THEN SHALL 展示 Run/Step/ToolCall/Observation/Hypothesis/ScriptArtifact/Conclusion 与版本元数据。

### Requirement: No Knowledge Dependency
V1 MUST NOT 依赖 Knowledge/RAG。
#### Scenario: Build
WHEN 检查依赖/配置
THEN MUST NOT 要求 Vector DB/Embedding/RAG/Knowledge Service。

### Requirement: TOP Case Acceptance
系统 SHALL 用“设备创建未持久化”做无堆栈 E2E 验收。
#### Scenario: Case Run
WHEN 执行固定 Case
THEN SHALL 产生多个 Hypothesis、使用 Runtime/Code Evidence、必要时生成补证据脚本、
最终 Conclusion 引用可验证 Evidence。

### Requirement: Log Template Mining Evidence
系统 SHALL 通过 Drain3 把应用日志聚类为模板并抽取参数，作为运行时证据。
#### Scenario: Mine Log Templates
WHEN Agent 需要压缩海量应用日志
THEN SHALL 调用日志模板挖掘工具（背后为 Drain3 sidecar）
AND SHALL 得到模板/簇/参数形式的证据
AND 远端 DTO SHALL NOT 泄漏到 Core。
