# log-evidence-code-context Specification

## Purpose
TBD - created by archiving change add-log-evidence-code-context. Update Purpose after archive.

## Requirements

### Requirement: Bounded Log Intake
系统 MUST 对进入模板挖掘和 LLM 的日志设置行数、单行字节、总字节、模板数、样本数和参数值数量上限。

#### Scenario: Oversized log input
GIVEN 一次调查返回超过配置上限的日志
WHEN 系统构建日志证据
THEN SHALL 按确定性规则截断
AND SHALL 在证据中记录原始数量、保留数量和截断原因
AND MUST NOT 把全部原始日志发送给 LLM。

### Requirement: Structured Prefix Separation
系统 SHALL 在调用 Drain3 前分离结构化日志字段与非结构化 message。

#### Scenario: Parse application log
GIVEN 日志包含 timestamp、level、pod、logger、traceId 和 message
WHEN 调用 Drain3
THEN SHALL 仅使用 message 进行模板挖掘
AND SHALL 保留结构化字段用于证据聚合和来源追踪。

### Requirement: Sensitive Data Redaction
系统 MUST 在持久化或发送给 LLM 前脱敏敏感日志参数。

#### Scenario: Secret in log parameter
GIVEN 日志包含 Authorization、token、password 或其他配置的敏感字段
WHEN 生成模板参数和代表样本
THEN MUST NOT 保存或输出原始敏感值
AND MAY 保存不可逆稳定 hash 用于关联。

### Requirement: Aggregated Log Evidence
系统 SHALL 将 Drain3 结果聚合为带来源信息的 LogEvidence，而不是直接暴露无界原始解析结果。

#### Scenario: Similar device errors
GIVEN 多条仅 deviceId、tenantId 不同的同类错误日志
WHEN 完成模板挖掘
THEN SHALL 形成同一模板证据
AND SHALL 包含 count、firstSeen、lastSeen、severity、代表样本和脱敏参数分布。

### Requirement: Deterministic Code Anchors
系统 SHALL 使用可版本化的确定性规则从日志证据中提取代码锚点。

#### Scenario: Exception and logger anchor
GIVEN 模板或样本包含 Java exception、logger、class 或 method
WHEN 提取锚点
THEN SHALL 输出 anchor type、value、sourceEvidenceId、confidence 和 ruleVersion
AND MUST NOT 使用 RAG、Embedding 或 Vector DB。

### Requirement: Version-bound Code Navigation
系统 MUST 使用 Incident 的 service 与 release/commit 绑定 CodeGraph 和 Snapshot 源码证据。

#### Scenario: Navigate from log to code
GIVEN 日志锚点可匹配代码符号且目标 Snapshot 为 READY
WHEN 查询 CodeGraph
THEN SHALL 获取候选符号或调用关系
AND SHALL 读取同一 Snapshot 的事实源码验证候选
AND 代码 Observation SHALL 记录 commit、文件和行号。

#### Scenario: Snapshot mismatch
GIVEN CodeGraph 候选不属于 Incident 对应的 release/commit
WHEN 构建 Evidence Bundle
THEN MUST NOT 使用该候选确认代码根因
AND SHALL 标记 VERSION_MISMATCH 或等待人工补充版本信息。

### Requirement: Provenance-preserving Evidence Bundle
系统 SHALL 为 LLM 构建有预算且可审计的 Evidence Bundle。

#### Scenario: Bundle for hypothesis validation
GIVEN 已有日志模板、代码锚点、图候选和源码片段
WHEN 调查循环请求下一轮推理
THEN Bundle SHALL 包含每项证据的来源、版本、截断和降级信息
AND SHALL 在配置的总字节或 token 预算内
AND MUST NOT 包含无来源的代码结论。

### Requirement: Evidence-backed Conclusion
日志到代码场景的根因结论 MUST 同时引用日志证据与事实源码证据。

#### Scenario: Insufficient code evidence
GIVEN 只有日志模板而没有版本匹配的源码证据
WHEN LLM 尝试确认代码根因
THEN Investigation MUST NOT 输出 ROOT_CAUSE_FOUND
AND SHALL 输出 INCONCLUSIVE 或 WAITING_FOR_HUMAN。

### Requirement: Auditable Degradation
Drain3、CodeGraph、Snapshot 或 LLM 不可用时系统 SHALL 安全降级并记录原因。

#### Scenario: Drain3 unavailable
GIVEN Drain3 MCP 调用失败或超时
WHEN 处理日志
THEN SHALL 保留有界且已脱敏的代表日志证据
AND SHALL 记录 LOG_MINER_UNAVAILABLE
AND 调查不得因静默丢失日志而产生肯定根因。

### Requirement: Repeatable Evaluation Fixtures
系统 MUST 提供可重复、机器可读的日志到代码评测夹具。

#### Scenario: Run default evaluation
GIVEN E01、E03、E05 三个固定能源故障场景
WHEN 在无外部服务环境运行默认评测
THEN SHALL 使用固定输入和 Fake/录制响应完成
AND SHALL 对 rootCauseId、expectedSymbols、requiredEvidenceTypes 和 forbiddenConclusions 执行断言。

### Requirement: Explicit Real E2E
系统 SHALL 提供显式启用的真实 Drain3 与真实联合诊断 E2E。

#### Scenario: Run real integration
GIVEN 真实 Drain3、CodeGraph、Snapshot 和 LLM 均已配置
WHEN 设置专用 E2E 开关运行测试
THEN SHALL 验证真实模板聚类、参数抽取、代码导航、源码读取和证据支持的结论
AND 默认 `mvn clean verify` SHALL NOT 依赖这些外部服务。

### Requirement: Existing Safety Boundaries
本 Change MUST 保持 No RAG、No arbitrary shell、No automatic production execution 和单实例 Java Web 边界。

#### Scenario: Boundary audit
WHEN 枚举依赖、工具与写操作入口
THEN MUST NOT 存在 RAG/Embedding/Vector DB 依赖
AND MUST NOT 存在 execute_shell
AND MUST NOT 存在自动执行诊断或修复脚本的生产路径。
