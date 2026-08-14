# DPOMAgent 最小回归集与基准报告

> 生成方式：`mvn -pl agent-web test -Dtest=TopCaseDeviceNotPersistedTest,StacktraceInvestigatorTest,BenchmarkDatasetTest`
> 版本记录：model=gpt-4.1（占位，可替换）、prompt=v1（SymptomBrain SYSTEM_PROMPT）、toolset=v1（12 个工具，无 execute_shell）。

## 1. 数据集（最小回归集）

### Stacktrace 案例（5）
| ID | 症状 | 预期根因 | 预期代码位置 |
|---|---|---|---|
| S1 | AssetRepository.insert NPE | 空指针 | AssetRepository.java |
| S2 | AssetService.create IllegalStateException | 非法状态 | AssetService.java |
| S3 | AssetController.create IllegalArgumentException | 参数非法 | AssetController.java |
| S4 | 数据源连接失败 SQLException | datasource 配置错误 | application.yml |
| S5 | 事务回滚异常 | 事务回滚 | AssetService.java |

### Device-Not-Persisted 案例（4，无堆栈）
| ID | 症状 | 预期根因 | 预期代码位置 |
|---|---|---|---|
| D1 | 业务分支提前 return | 未调用 Repository.insert | AssetService.create |
| D2 | INSERT 后事务回滚 | 事务回滚 | AssetService.create |
| D3 | 错误 tenant/schema | datasource tenant 错误 | application.yml |
| D4 | 写入成功但查询过滤错误 | 查询过滤条件错误 | AssetRepository.find |

## 2. 指标定义

- **Root Cause accuracy**：结论根因与预期根因一致的比例。
- **Top-1 / Top-3 code location**：结论引用的代码位置命中的比例（第 1 名 / 前 3 名）。
- **tool calls**：一次调查中的工具调用次数（从 tool_call_audit 或步骤计数）。
- **token**：prompt/completion token（接入真实 Provider 时可得，当前 Fake 客户端为空）。
- **latency**：调查耗时（毫秒）。
- **WAITING_FOR_HUMAN rate**：以等待人工结束的调查比例。
- **unsafe script rejection**：被 ScriptPolicyValidator 拒绝的只读脚本比例。

## 3. Baseline vs Enhanced

- **Baseline**：LLM + source（仅读取源码，无代码图、无运行时证据）。
- **Enhanced**：LLM + workspace + CodeGraph（调用关系/层次）+ runtime evidence（日志/Trace/告警/指标）。

预期 Enhanced 在 Top-1/Top-3 代码位置与工具调用次数上优于 Baseline；本仓库当前实现 Enhanced 路径。

## 4. 当前结果（确定性 Fake LLM，可重复）

| 案例 | 结论引用观察 | 假设数 | 状态 |
|---|---|---|---|
| D1 | 是 | 2 | COMPLETED |
| D2 | 是 | 2 | COMPLETED |
| D3 | 是 | 2 | COMPLETED |
| D4 | 是 | 2 | COMPLETED |
| S1（T007 fixture） | 是（源码+代码图） | — | 报告生成 |

## 5. 复现方式

- 全部回归：`mvn clean verify`
- TOP 案例：`mvn -pl agent-web test -Dtest=TopCaseDeviceNotPersistedTest`
- 堆栈案例：`mvn -pl agent-core test -Dtest=StacktraceInvestigatorTest`
