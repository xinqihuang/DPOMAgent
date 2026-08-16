## MODIFIED Requirements

### Requirement: Default Test Isolation
默认测试（`mvn clean verify`）SHALL NOT 依赖本机 MySQL、Redis、DeepSeek、Drain3 或 CodeGraph 的 stdio MCP 子进程。
#### Scenario: Offline Build
WHEN 在无外部中间件/无 API Key/未安装 CodeGraph 的干净环境执行 `mvn clean verify`
THEN 默认测试 SHALL 使用内存数据库（H2 MySQL 模式）与 Mock/桩
AND 真实外部依赖的端到端测试 SHALL 由 `DPOM_E2E=true` 显式启用（默认跳过）
AND CodeGraph stdio 子进程 SHALL NOT 在默认测试中启动。

### Requirement: CodeGraph via MCP
CodeGraph SHALL 通过 CodeGraph 官方 stdio MCP（`codegraph serve --mcp`）接入，默认工具 `codegraph_explore`，并按需显式启用 `codegraph_search`/`codegraph_callers`/`codegraph_callees`/`codegraph_impact`/`codegraph_node`/`codegraph_files`/`codegraph_status`；返回结果解析为内部 Symbol/CallStep/ClassHierarchy DTO。
#### Scenario: Graph Query
WHEN Agent 调用 find_symbol/find_callers/find_callees/find_call_chain/find_class_hierarchy
THEN Adapter SHALL 调用对应 CodeGraph MCP 工具
AND 远端文本结果经版本化解析器转为内部 DTO，远端 DTO SHALL NOT 泄漏到 Core。

### Requirement: Graph and Source Separation
CodeGraph SHALL 仅用于导航，源码事实 SHALL 来自与 Incident 同一 commit 的 Snapshot/Git。
#### Scenario: Graph Candidate
WHEN CodeGraph 返回候选调用链
THEN Agent SHOULD 读取真实源码
AND MUST NOT 仅凭静态图宣称运行时根因。

