# DPOM Investigation Agent Specification（current truth）

本文件是 \`investigation-agent\` 的 current truth，与实际代码一致（MCP 接入、模块依赖方向、默认测试隔离）。

## Purpose
提供研发侧症状驱动故障调查能力：无异常堆栈时按业务症状形成假设并取证，有异常堆栈时解析堆栈并读源码/查调用图，证据不足时生成只读诊断脚本，最终输出引用 Observation 的结论。V1 不建设 Knowledge/RAG/Embedding/Vector DB。

## Requirements

### Requirement: Single Java Web Application
系统 SHALL 作为研发环境单实例 Java Web 应用运行。
#### Scenario: Start
GIVEN JDK21/MySQL/外部服务配置就绪
WHEN \`java -jar agent-web.jar\`
THEN SHALL 启动 Spring MVC
AND SHALL NOT 依赖 Docker/K8s/注册中心。

### Requirement: Module Dependency Direction
在项目内部模块依赖中，agent-core 只依赖 agent-common、不依赖任何 adapter；Port/DTO 契约 SHALL 放在 agent-common；agent-web SHALL 作为 composition root 组装 Core 与 Adapter。本要求不限制 agent-core 使用 MyBatis、Jackson、Redis 等第三方库。
#### Scenario: Dependency Graph
WHEN 检查项目内部模块依赖
THEN agent-core SHALL 只依赖 agent-common，不依赖 adapter
AND agent-web SHALL 依赖 agent-core 与 agent-adapter-{llm,runtime,codegraph}
AND adapter SHALL 只依赖 agent-common。

### Requirement: Default Test Isolation
默认测试（`mvn clean verify`）SHALL NOT 依赖本机 MySQL、Redis、DeepSeek、Drain3 或 CodeGraph 的 stdio MCP 子进程。
#### Scenario: Offline Build
WHEN 在无外部中间件/无 API Key/未安装 CodeGraph 的干净环境执行 `mvn clean verify`
THEN 默认测试 SHALL 使用内存数据库（H2 MySQL 模式）与 Mock/桩
AND 真实外部依赖的端到端测试 SHALL 由 `DPOM_E2E=true` 显式启用（默认跳过）
AND CodeGraph stdio 子进程 SHALL NOT 在默认测试中启动。

### Requirement: LLM Provider Isolation
系统 SHALL 通过 ModelClient Adapter 隔离具体 LLM Provider。
#### Scenario: Replace Provider
WHEN 更换模型 Provider
THEN Core SHALL 不修改
AND Provider DTO SHALL NOT 泄漏到 Core。

### Requirement: Persistent Investigation
系统 SHALL 持久化 Incident/Investigation/Run/Step/Observation/Hypothesis/Conclusion。
#### Scenario: Resume
GIVEN 调查已进行数步
WHEN 应用重启
THEN SHALL 从数据库恢复状态
AND 不依赖原 LLM 会话。

### Requirement: MyBatis XML Mapper Persistence
持久化层 SHALL 使用 MyBatis XML Mapper：SQL 全部放 XML（一个 Mapper 一个 XML，namespace=接口全限定名），
Java Mapper 只保留类型安全方法签名，禁止注解 SQL、字符串 SQL、Map 弱类型胶水。AUTO_INCREMENT 插入 SHALL 使用类型化
mutable command 参数（含可回填 Long id）。record 查询 SHALL 使用显式 resultMap/constructor 映射，禁止 SELECT *。
生产目标库为 RDS for MySQL 8.0，SQL 兼容 MySQL 8.0；H2 仅用于快速测试，真实兼容性由真实 MySQL 8.0 契约（Testcontainers 或受控外部实例，见 design D7）证明。契约测试必须真实执行并记录 REAL_EXECUTED 路径，不得以 mock、静态扫描或跳过冒充通过。
#### Scenario: No SQL In Java
WHEN 检查 Mapper Java 接口
THEN SHALL NOT 出现 @Select/@Insert/@Update/@Delete/@*Provider 或字符串 SQL
AND 所有 SQL SHALL 位于 XML mapper。
#### Scenario: Typed Insert Command
WHEN 插入带自增主键的记录
THEN SHALL 使用类型化 mutable command 参数回填 Long id
AND SHALL NOT 使用 Map<String,Object> 或 insertRaw 弱类型胶水。
#### Scenario: Explicit Column Mapping
WHEN 查询 record
THEN SHALL 使用显式列清单与 resultMap/constructor 映射
AND SHALL NOT 使用 SELECT * 或依赖数据库列顺序。
#### Scenario: MySQL 8.0 Auto Clean-Install Baseline
WHEN 启动应用且目标库为全新空 MySQL 8.x（无 flyway_schema_history）
THEN 应用 SHALL 自动执行受版本控制的 baseline 并建立 Flyway version 9 基线
AND SHALL NOT 修改任何已发布 migration 的 checksum
AND 非空 schema、已有历史或非 MySQL 环境 SHALL NOT 被误初始化
AND 部分表存在、版本不匹配或 baseline 失败 SHALL fail-closed 并终止启动。

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
THEN SHALL 考虑请求到达、业务分支、持久化调用、SQL/事务、datasource/schema/tenant、异步链路、读取侧和发布回归
AND SHALL NOT 无证据直接确认根因。

### Requirement: Hypothesis Status
Hypothesis SHALL 使用 PROPOSED/VALIDATING/VALIDATED/INVALIDATED/INCONCLUSIVE。
#### Scenario: Contradiction
WHEN Observation 与 H1 冲突
THEN H1 SHALL 变为 INVALIDATED
AND 保留否定证据。

### Requirement: Runtime Evidence
Agent SHALL 通过 RuntimeEvidenceClient 调用运行时证据服务。
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

### Requirement: CodeGraph via MCP
CodeGraph SHALL 通过 CodeGraph 官方 stdio MCP（`codegraph serve --mcp`）接入，默认工具 `codegraph_explore`，并按需显式启用 `codegraph_search`/`codegraph_callers`/`codegraph_callees`/`codegraph_impact`/`codegraph_node`/`codegraph_files`/`codegraph_status`；返回结果解析为内部 Symbol/CallStep/ClassHierarchy DTO。
#### Scenario: Graph Query
WHEN Agent 调用 find_symbol/find_callers/find_callees/find_call_chain/find_class_hierarchy
THEN Adapter SHALL 调用对应 CodeGraph MCP 工具
AND 远端文本结果经版本化解析器转为内部 DTO，远端 DTO SHALL NOT 泄漏到 Core。

### Requirement: Controlled Workspace
Agent SHALL 只在 Snapshot 根目录搜索/读取源码。
#### Scenario: Path Escape
WHEN path 包含 ../、绝对路径逃逸或 symlink escape
THEN SHALL 拒绝。

### Requirement: Code Tools
LLM SHALL 至少可用 list_files/search_text/read_source/find_symbol/find_callers/find_callees/find_call_chain/find_class_hierarchy/search_logs/query_trace/query_alerts/query_metrics/mine_log_templates。
#### Scenario: No Shell Tool
WHEN 枚举 Toolset
THEN MUST NOT 存在 execute_shell。

### Requirement: Graph and Source Separation
CodeGraph SHALL 仅用于导航，源码事实 SHALL 来自与 Incident 同一 commit 的 Snapshot/Git。
#### Scenario: Graph Candidate
WHEN CodeGraph 返回候选调用链
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

### Requirement: Snapshot Cache
系统 MAY 用 Redis 缓存快照解析结果（避免重复 MCP 调用），但缓存失败 MUST NOT 影响主流程。
#### Scenario: Cache Miss
WHEN 缓存未命中或 Redis 不可用
THEN SHALL 降级为直接解析快照
AND 调查流程 SHALL NOT 中断。

### Requirement: No Knowledge Dependency
V1 MUST NOT 依赖 Knowledge/RAG/Embedding/Vector DB。
#### Scenario: Build
WHEN 检查依赖/配置
THEN MUST NOT 要求 Vector DB/Embedding/RAG/Knowledge Service。

### Requirement: TOP Case Acceptance
系统 SHALL 用“设备创建未持久化”做无堆栈 E2E 验收。
#### Scenario: Case Run
WHEN 执行固定 Case
THEN SHALL 产生多个 Hypothesis、使用 Runtime/Code Evidence、必要时生成补证据脚本、最终 Conclusion 引用可验证 Evidence。

### Requirement: Log Template Mining via Drain3 MCP
系统 SHALL 通过 Drain3 的 MCP（McpLogTemplateMinerClient，工具 train_logs/list_templates）把应用日志聚类为模板并抽取参数。
#### Scenario: Mine Log Templates
WHEN Agent 需要压缩海量应用日志
THEN SHALL 调用 mine_log_templates（lines 为 string array）
AND SHALL 得到模板/簇/参数形式的证据
AND 远端 DTO SHALL NOT 泄漏到 Core。
