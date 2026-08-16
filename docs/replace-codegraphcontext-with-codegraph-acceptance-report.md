# replace-codegraphcontext-with-codegraph 验收报告

日期：2026-08-15
状态：验收通过并已归档（`2026-08-15-replace-codegraphcontext-with-codegraph`，current truth 已同步）
编码：UTF-8（无 BOM，与仓库既有 Markdown 一致）

## 1. 结果总览

- `openspec validate replace-codegraphcontext-with-codegraph --strict` → valid。
- `mvn clean verify` → BUILD SUCCESS：**291 tests，0 failures，0 errors，7 skipped**。
- `test-fixtures/energy-platform-demo` `mvn clean compile` → BUILD SUCCESS（5 源文件，release 21）。
- 分模块（fresh surefire 汇总）：
  - agent-adapter-llm：8（0 skip）
  - agent-adapter-runtime：6（0 skip）
  - agent-adapter-codegraph：39（0 skip）
  - agent-core：101（1 skip）
  - agent-web：137（6 skip）
  - 合计：291 = 8 + 6 + 39 + 101 + 137；skipped 7 = 0 + 0 + 0 + 1 + 6。
- 本 Change 新增测试：解析器 13、受控进程 4、版本校验 3、stdio 客户端 15、装配边界 4、
  仓库注册表 8、fixture 契约 2、真实 CodeGraph E2E 1（显式启用时通过；默认 verify 中 skip）。

## 2. CodeGraph 固定版本与 transport

- 固定版本：**v1.5.0**（2026-07-21 发布，MIT；发布物 win32-x64/linux-x64/darwin + SHA256SUMS）。
  配置于 `dpom.codegraph.version`，`CodeGraphResponseParser.FORMAT_VERSION=codegraph-1.5.0`。
- transport：Java MCP SDK 0.10.0 `StdioClientTransport` + `ServerParameters`（`codegraph serve --mcp`，
  内部 ProcessBuilder，不经 shell，不自实现 JSON-RPC framing）。
- **版本校验接入运行时装配**：`CodeGraphAdapterConfiguration` 在创建 stdio 客户端前调用
  `CodeGraphVersionValidator.validate(executable, dpom.codegraph.version)`，executable 缺失或版本不匹配即
  fail closed（Spring 装配失败）。装配测试覆盖 executable missing / version mismatch / version match。

## 3. 工具映射与 projectPath 受控

| 内部 port 方法 | CodeGraph MCP 工具 | 备注 |
|---|---|---|
| findSymbol | codegraph_search | 位置列表解析 |
| findCallers | codegraph_callers | 列表解析 |
| findCallees | codegraph_callees | 列表解析 |
| findCallChain | codegraph_explore | 结构化 call paths，不可靠时安全降级返回空 |
| findClassHierarchy | codegraph_node | 从签名推断祖先，不可靠时降级空祖先 |
| findImpact（port 默认方法） | codegraph_impact | 有界图摘要，不破坏现有调用方 |

默认工具 `codegraph_explore`；确定性 DTO 工具经 `CODEGRAPH_MCP_TOOLS=explore,status,node,search,callers,callees,impact,files` 显式启用。
官方 MCP 返回文本（Markdown）：封装版本化 `CodeGraphResponseParser` + fixture contract test，未知/畸形 fail closed。

**projectPath 不可绕过 Repository Registry**：resolveSnapshot 经 Registry 返回受控 snapshotId（real path）；
每个 find*/getSnapshot 都先把 snapshotId 经 `RepositoryRegistry.resolveByProjectPath` 反查并做 real-path containment
校验，未注册/越界/`..`/symlink escape/任意绝对路径一律 fail closed，禁止任意路径直接进入 MCP 参数。

## 4. profile 隔离与安全边界

- development（`dpom.codegraph.enabled=true`）：装配 `CodeGraphStdioClient` + `ConfigRepositoryRegistry` +
  `CodeGraphProcessParameters` + `CodeGraphVersionValidator`，装配前校验 executable 与版本。
- production（缺省 false）：**无 active CodeGraph stdio adapter/进程/源码访问**；仅保留 fail-closed 的
  `DisabledCodeGraphClient` port bean（不是 CodeGraph adapter，不启动进程、不访问源码，调用 fail closed），
  以满足 core 对 CodeGraphClient port 的依赖。
- 受控进程：executable 来自服务端配置，参数固定 `serve --mcp`，不经过 cmd/powershell/sh/bash，无命令/参数/环境注入面，
  无 arbitrary shell tool，LLM 不触发启动/索引/同步。
- 遥测/更新检查禁用：`CODEGRAPH_TELEMETRY=0`、`DO_NOT_TRACK=1`、`CODEGRAPH_NO_UPDATE_CHECK=1`（代码固定）。
- Repository Registry：serviceCode+release/commit → projectPath 确定映射，未知服务/commit mismatch/越界/symlink escape 均 fail closed，
  无「选第一个仓库」；构建/启动不下载。
- 内部 DTO 隔离：CodeGraph MCP DTO 不泄漏到 core，保留 `CodeGraphClient`/`Symbol`/`CallStep`/`ClassHierarchy`/`CodeSnapshot`。

## 5. 真实 E2E 状态

- `CodeGraphStdioE2ETest` 由 `DPOM_CODEGRAPH_E2E=true` 显式启用，默认跳过。使用本机 CodeGraph **1.5.0**、
  npm Windows launcher 与已索引的 energy-platform-demo/asset-service 实测：**1 test，0 failures，0 errors，0 skipped**。
- MCP 握手确认服务端 `name=codegraph, version=1.5.0`，真实执行 `codegraph_search` 并检索到 `AssetService`。
- 修复了测试从 `agent-web` 模块工作目录运行时无法找到仓库根目录 fixture、导致永久 `NOT_EXECUTED` 的问题；
  显式启用 E2E 后 fixture 缺失现在会失败，不再用 Assumption 掩盖配置错误。
- E2E 先 `resolveSnapshot` 再用返回的受控 snapshotId（不经任意路径）。
- 既有真实联合 E2E（CombinedE2ETest / DiagnosticRegressionE2ETest / Log4jStacktraceE2ETest）仍由各自环境变量显式启用、默认跳过，已改为 stdio 客户端。

## 6. 残留扫描（CodeGraphContext/CGC）

非历史文件逐项说明：
- `openspec/specs/investigation-agent|log-evidence-code-context|investigation-operability/spec.md`：current truth 仍含 CodeGraphContext/CGC 表述，
  由本 Change 的 MODIFIED delta spec 承载修正，按约定「归档前不直接篡改 current truth」，归档时经 delta 同步更新。
- `docs/architecture/ADR-002-codegraph-selection.md`、`docs/tasks/T210-codegraph-migration.md`、本 Change 的 proposal/design/tasks：
  作为「被替换对象」与迁移描述出现，不把 CodeGraphContext 描述为当前实现。
- 历史归档（`openspec/changes/archive/*`）与历史验收报告（closure/e2e/real-diagnostic-regression）：保留历史事实，未改动。
- Java 主/测试源码、README、local-environment.example.md、energy-platform-demo README、application.yml 中 CodeGraphContext/CGC 已清除。

## 7. 已知风险

- CodeGraph 文本格式随版本漂移：解析器以 FORMAT_VERSION + fixture 锁定 v1.5.0，升级需同步 fixture 与固定版本号。
- findCallChain/findClassHierarchy 依赖 explore/node 文本，无法可靠还原时降级为空（不伪造），源码事实由 Snapshot 补齐。
- 版本校验在 development 首次装配时跑一次 `codegraph version` 子进程（非 shell、读 stdout），失败即 fail。
- 真实 CodeGraph stdio E2E 已在 Windows/npm 安装的 1.5.0 上通过；正式部署仍需使用受控离线安装包并记录 checksum。

## 8. 主要修改文件

- 新增：`agent-common/.../codegraph/{RepositoryRegistry,RegisteredRepository,CommitMismatchException}.java`。
- 新增：`agent-adapter-codegraph/.../codegraph/{CodeGraphStdioClient,CodeGraphResponseParser,CodeGraphProcessParameters,CodeGraphVersionValidator,DisabledCodeGraphClient}.java`；
  重写 `CodeGraphAdapterConfiguration.java`（版本校验接入装配）；删除 `McpCodeGraphClient.java`。
- 新增：`agent-web/.../config/{ConfigRepositoryRegistry,RepositoryRegistryProperties,RepositoryRegistryConfiguration}.java`；
  修改 `application.yml`、`MeteredCodeGraphClient.java`、`CodeGraphClient.java`。
- 测试新增/删除：见第 1 节；`McpCodeGraphClientTest.java` 已删除。
- 文档：`README.md`、`docs/architecture/ADR-002-codegraph-selection.md`、`docs/local-environment.example.md`、
  `test-fixtures/energy-platform-demo/README.md`、`docs/tasks/T007-*.md`、`docs/tasks/T210-codegraph-migration.md`。

## 9. 验收命令复现

- `openspec validate replace-codegraphcontext-with-codegraph --strict`
- `mvn clean verify`（JDK 21 + Maven 3.9.16，环境变量显式指定）
- `mvn clean compile`（test-fixtures/energy-platform-demo）
- `DPOM_CODEGRAPH_E2E=true`、`DPOM_CODEGRAPH_EXECUTABLE=<codegraph 1.5.0 launcher>`、
  `DPOM_CODEGRAPH_VERSION=1.5.0` 下运行 `CodeGraphStdioE2ETest`
