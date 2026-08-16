## MODIFIED Requirements

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

### Requirement: Auditable Degradation
Drain3、CodeGraph、Snapshot 或 LLM 不可用时系统 SHALL 安全降级并记录原因。

#### Scenario: Drain3 unavailable
GIVEN Drain3 MCP 调用失败或超时
WHEN 处理日志
THEN SHALL 保留有界且已脱敏的代表日志证据
AND SHALL 记录 LOG_MINER_UNAVAILABLE
AND 调查不得因静默丢失日志而产生肯定根因。

### Requirement: Explicit Real E2E
系统 SHALL 提供显式启用的真实 Drain3 与真实联合诊断 E2E。

#### Scenario: Run real integration
GIVEN 真实 Drain3、CodeGraph、Snapshot 和 LLM 均已配置
WHEN 设置专用 E2E 开关运行测试
THEN SHALL 验证真实模板聚类、参数抽取、代码导航、源码读取和证据支持的结论
AND 默认 `mvn clean verify` SHALL NOT 依赖这些外部服务。

