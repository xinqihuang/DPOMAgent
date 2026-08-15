# Real Diagnostic Regression Specification

## ADDED Requirements

### Requirement: Answer-Leak-Free Fixtures
系统 MUST 校验 fixture 不向模型泄露根因答案。
#### Scenario: Symptom leaks root cause
GIVEN incident.json 的 symptom 包含 rootCauseId 或 expectedSymbols
WHEN 校验 fixture
THEN SHALL 失败并报告泄露字段。

#### Scenario: Log injects answer
GIVEN logs.txt 某行以标签/提示形式包含 rootCauseId（非 stack frame）
WHEN 校验 fixture
THEN SHALL 失败；stack frame（at FQN.method(File.java:line)）视为合法日志。

### Requirement: Expected Excluded From Prompt
expected.json SHALL 仅供评估器使用，MUST NOT 进入 LLM prompt 或 EvidenceBundle。
#### Scenario: Prompt construction
WHEN 构建 LLM 请求或 EvidenceBundle
THEN MUST NOT 读取 expected.json 或把 rootCauseId 作为提示注入。

### Requirement: Repeatable Benchmark Runner
系统 SHALL 提供统一 Runner 执行全部案例，单案例失败不阻断其他案例，并原子写出 machine-readable 结果。
#### Scenario: One case fails
GIVEN 一个案例失败
WHEN 运行 Runner
THEN 其余案例仍被记录
AND 输出 diagnostic-regression.json 包含全部案例结果。

### Requirement: Quantitative Metrics
系统 SHALL 计算机器可读指标：caseCount/executedCount/passedCount/failedCount、rootCauseAccuracy、evidenceGroundingRate、completionRate、inconclusiveRate、latency p50/p95。
#### Scenario: Overall passed
WHEN 任一 mandatory 案例未执行、被跳过或断言失败
THEN 总体 passed SHALL 为 false。

### Requirement: Anti-Cheat Enforcement
系统 MUST 通过负向测试证明：错误 rootCauseId 失败、缺 LOG/VERIFIED SOURCE 引用失败、部分未执行总体失败、旧结果文件不被误用。
#### Scenario: Wrong root cause
GIVEN 证据包含正确符号但 Conclusion.rootCauseId 错误
THEN 评估 SHALL 失败并报告 ROOT_CAUSE_MISMATCH。

### Requirement: Real Combined Regression
系统 SHALL 提供显式开关的三案例真实联合回归，真实模式不得使用 recorded/fake 客户端。
#### Scenario: Default build
WHEN 运行默认 mvn clean verify
THEN 外部 E2E SHALL 跳过。

### Requirement: Energy Platform Fixtures
系统 SHALL 提供 test-fixtures/energy-platform-demo 的最小可编译 Spring 风格示例源码，覆盖 asset-service/telemetry-service/gateway-service。
#### Scenario: Compile
WHEN 编译示例源码
THEN SHALL 成功且与 eval workspace/commit 映射一致。
