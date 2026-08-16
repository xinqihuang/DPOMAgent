# Design: replace CodeGraphContext with CodeGraph stdio MCP

## Context

现有 `McpCodeGraphClient` 通过 Spring AI 的 `HttpClientSseClientTransport` 接入旧 CodeGraphContext 的
`/api/v1/mcp/sse`，工具为 `list_indexed_repositories` / `find_code` / `analyze_code_relationships`，
`resolveSnapshot` 用「路径包含 serviceCode 否则选第一个仓库」的脆弱回退。Java MCP SDK 0.10.0（随
`spring-ai-starter-mcp-client` 引入）已提供 `StdioClientTransport` + `ServerParameters`（内部走
`ProcessBuilder`，不经 shell），无需自实现 JSON-RPC framing。

## Goals / Non-Goals

**Goals:**
- 用官方 CodeGraph stdio MCP（`codegraph serve --mcp`）替换 SSE，受控进程 + 固定参数 + 固定禁用遥测环境。
- 建立 Repository Registry 的确定性 serviceCode+commit → projectPath 映射，含 real-path containment，fail closed。
- development profile 才装配 active CodeGraph stdio adapter（装配前校验 executable 与固定版本）；production 无 active
  adapter/进程/源码访问，仅保留 fail-closed 的禁用态 port。
- 版本化文本解析器 + fixture contract test + 未知格式 fail closed；内部 DTO 不泄漏、CodeGraph DTO 不进 core。

**Non-Goals:**
- 不在本 Change 实现真实 OBS、真实 Drain3 之外的额外基础设施，不下载/安装 CodeGraph 二进制。
- 不实现 CodeGraph 的索引/同步命令触发（索引是运维显式 CLI 动作，不暴露给 LLM）。
- 不把图结果当源码事实：源码事实仍来自 Snapshot Workspace。

## Decisions

### D1 stdio transport 用 Java MCP SDK 的 StdioClientTransport
`ServerParameters.builder(executable).args('serve','--mcp').env(固定遥测禁用).build()` +
`McpClient.sync(new StdioClientTransport(params)).build()`。该 transport 内部用 `ProcessBuilder` 直接拉起进程，
不经 cmd/powershell/sh/bash，也不自写 JSON-RPC framing。备选（自写 stdio framing）被否决：违反约束且易错。

### D2 受控进程与版本校验接入装配
`CodeGraphProcessParameters` 只暴露 `executablePath`（来自服务端配置），参数与 `env` 由代码固定；
`CodeGraphVersionValidator` 用 `ProcessBuilder` 跑一次 `codegraph version`（读 stdout，非 shell）与配置的
`dpom.codegraph.version` 比对。`CodeGraphAdapterConfiguration` 在创建 stdio 客户端前调用 `validate`，
executable 缺失或版本不匹配即 fail closed（Spring 装配失败），不静默降级。

### D3 RepositoryRegistry 是 agent-common 的 port，配置实现放 agent-web
`RepositoryRegistry.resolve(serviceCode, commit)` → `RegisteredRepository(serviceCode, release, commit, snapshotRoot)`，
另加 `resolveByProjectPath(projectPath)` 反查校验。实现 `ConfigRepositoryRegistry` 读 `dpom.repositories`
（serviceCode→release/commit/path），未知服务抛 `SnapshotNotFoundException`，commit 不一致抛 `CommitMismatchException`；
绝无「选第一个」。`CodeGraphStdioClient.resolveSnapshot` 委托给 registry，返回
`CodeSnapshot(snapshotRoot, serviceCode, commit, snapshotRoot, READY)`；每个 find*/getSnapshot 都先把 snapshotId 经
`resolveByProjectPath` 反查为受控 projectPath 再进 MCP 参数，禁止任意路径直接进入 MCP 参数。

### D4 工具映射与文本解析
官方 MCP 工具全部返回文本 Markdown，无 JSON 结构化输出。故 `CodeGraphResponseParser`（带 `FORMAT_VERSION`，
对齐固定版本 v1.5.0）解析：search/callers/callees 的 `**... (N found)**` + `- name (kind) - file:line` 列表、
impact 的 `**Impact: ... N symbols**` + `file:` 分组、node/explore 的 location/signature/code fence。
未知/畸形格式直接抛 `CodeGraphQueryException`（fail closed）。findCallChain/findClassHierarchy 走 explore/node，
解析不出时安全降级返回空并记录原因，不伪造。

### D5 profile 隔离用条件装配
`CodeGraphAdapterConfiguration` 用 `@ConditionalOnProperty(name='dpom.codegraph.enabled')` 区分两种装配：
development（true）装配 `CodeGraphStdioClient` + `CodeGraphProcessParameters` + `CodeGraphVersionValidator`，
并在创建客户端前校验 executable 与版本；production（缺省 false）装配 `DisabledCodeGraphClient`（fail-closed 禁用态 port，
不是 CodeGraph adapter，不启动进程、不访问源码）。测试用 `ApplicationContextRunner` 断言两种装配及版本校验 fail closed。

### D6 impact 进入 port 的兼容默认方法
`CodeGraphClient` 增加 `default List<Symbol> findImpact(String snapshotId, String symbol) { return List.of(); }`，
`CodeGraphStdioClient` 覆写为 `codegraph_impact` 的有界摘要；`LocalCodeGraphClient`/`MeteredCodeGraphClient`/
`RecordedCodeGraphClient` 及所有 mock 均无需改动，不破坏现有调用方。

### D7 版本固定与离线安装
固定版本 v1.5.0（2026-07-21 发布，MIT，发布物含 win32-x64/linux-x64/darwin + SHA256SUMS）。
构建与启动不下载；正式部署用离线 zip/tar + SHA256SUMS 校验；真实 E2E 未安装固定版本时报 NOT_EXECUTED。

## Risks / Trade-offs

- [Markdown 文本格式随版本漂移] → 解析器带 FORMAT_VERSION 并用 fixture contract test 锁定 v1.5.0 格式，
    未知格式 fail closed；升级版本需更新 fixture 与固定版本号。
- [explore/node 文本无法可靠还原 call chain/hierarchy] → 安全降级返回空 + 记录原因，不伪造；源码事实由 Snapshot 补齐。
- [stdio 子进程生命周期/僵尸进程] → 由 Java MCP SDK transport 管理进程生命周期，优雅停机时 closeGracefully；
    仅 development 装配，production 无子进程。
- [版本校验多一次子进程] → 仅在 development 首次装配时校验一次，校验失败即 fail，不静默降级。

