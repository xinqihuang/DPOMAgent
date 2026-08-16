# Replace CodeGraphContext with CodeGraph

## Why

当前代码图能力通过旧 `CodeGraphContext` 的 MCP-over-SSE（`dpom.codegraph.mcp-base-url` + `/api/v1/mcp/sse`，
工具 `list_indexed_repositories` / `find_code` / `analyze_code_relationships`）接入，且 `resolveSnapshot`
依赖「找不到就选第一个仓库」的脆弱回退。ADR-002 已选定 `colbymchenry/codegraph`（统一名称为 CodeGraph）为标准实现：
本地 SQLite、原生 Java/Spring 路由识别、符号搜索/调用关系/影响面/`codegraph_explore`，许可证 MIT，官方 MCP 为
stdio 传输。本次把实际代码切到 CodeGraph 官方 stdio MCP，并补齐受控进程、Repository Registry、profile 隔离、
遥测禁用与确定性解析边界。

## What Changes

- **BREAKING** 删除对 CodeGraphContext MCP-over-SSE 的依赖：`dpom.codegraph.mcp-base-url`、
  `/api/v1/mcp/sse`、`list_indexed_repositories`、`find_code`、`analyze_code_relationships`。
- 改为 CodeGraph 官方 stdio MCP：`codegraph serve --mcp`，默认工具 `codegraph_explore`，
  按确定性 DTO 需要经 `CODEGRAPH_MCP_TOOLS` 显式启用 `status,node,search,callers,callees,impact,files`。
- 使用 Java MCP SDK（Spring AI）的 stdio transport，禁止自行实现 JSON-RPC framing。
- CodeGraph 启动为受控进程：executable 路径来自服务端配置；参数由代码固定构造（`serve --mcp`）；
  不经过 cmd.exe/powershell/sh/bash；不新增 arbitrary shell tool；LLM 不调用启动/索引/同步命令。
- projectPath 由 Repository Registry / 快照根解析，并做 real-path containment 校验；
  serviceCode + release/commit 确定映射，找不到或 commit 不一致 fail closed。
- CodeGraph 的 active stdio adapter 仅在 development profile 装配（装配前校验 executable 存在与固定版本）；
  production profile 无 active adapter/进程/源码访问，仅保留 fail-closed 的禁用态 port。
- 强制关闭遥测与更新检查：`CODEGRAPH_TELEMETRY=0`、`DO_NOT_TRACK=1`、`CODEGRAPH_NO_UPDATE_CHECK=1`；
  禁止运行时 update check/自动升级/自动下载；不在构建或启动时从公网下载。
- 固定并记录经校验的 CodeGraph 版本（v1.5.0）；正式部署用离线安装包 + SHA256SUMS checksum。
- 继续保留内部 `CodeGraphClient` port 与 `Symbol`/`CallStep`/`ClassHierarchy`/`CodeSnapshot` DTO；
  CodeGraph MCP DTO 不得泄漏到 core；impact 能力以兼容默认方法进入 port。
- 工具映射：findSymbol→`codegraph_search`/node、findCallers→`codegraph_callers`、findCallees→`codegraph_callees`、
  findCallChain→`codegraph_explore` 结构化 call paths（不可解析时安全降级）、findClassHierarchy→`codegraph_explore`/node
  （不可解析时安全降级）、impact→内部 port 有界摘要。
- 官方 MCP 输出为文本（Markdown）：封装版本化解析器、fixture contract test、未知格式 fail closed。
- 图只作导航；最终源码事实仍来自准确 commit snapshot；证据记录 provider=codegraph、版本、projectPath 安全标识、
  commit 与降级原因。
- 清理所有非历史文件中的 CodeGraphContext/CGC 表述（Java 注释、测试名/变量、metrics/health 名称、fixtures、
  E2E、README、架构、部署、本地环境模板）。

## Capabilities

### New Capabilities
- `codegraph-mcp-integration`: CodeGraph 官方 stdio MCP 接入、受控进程生命周期、Repository Registry 映射、
  profile 隔离、遥测/更新禁用、版本固定、确定性文本解析与 fail-closed 降级、工具到内部 DTO 的映射契约。

### Modified Capabilities
- `investigation-agent`: CodeGraph via MCP 需求改为 CodeGraph stdio 工具契约；Graph and Source Separation 与
  Default Test Isolation 中 CodeGraphContext/CGC 术语更新为 CodeGraph，并补强 version 绑定与默认测试隔离语义。
- `log-evidence-code-context`: 场景中 CodeGraphContext 术语更新为 CodeGraph，Version-bound Code Navigation
  与 Auditable Degradation 保持导航/降级语义。
- `investigation-operability`: 外部适配器与 health 场景中 CGC/CodeGraphContext 术语更新为 CodeGraph，
  adapter 标签保持低基数。

## Impact

- `agent-common`：新增 `RepositoryRegistry` port 与 `RegisteredRepository`/`CommitMismatchException`；
  `CodeGraphClient` 增加兼容默认方法 `findImpact` 并清理注释。
- `agent-adapter-codegraph`：删除 `McpCodeGraphClient`（SSE），新增 `CodeGraphStdioClient`、
  版本化 `CodeGraphResponseParser`、受控 `CodeGraphProcessParameters`/`CodeGraphVersionValidator`；
  重写 `CodeGraphAdapterConfiguration`（development 条件装配 + stdio transport）。
- `agent-web`：新增 `ConfigRepositoryRegistry`（serviceCode+commit 确定映射 + real-path containment）；
  application.yml 移除 `dpom.codegraph.mcp-base-url`，新增 `dpom.codegraph.*`（enabled/executable-path/version/mcp-tools）。
- 测试：stdio 初始化/固定 tool list/进程与版本校验/timeout/transport closed、注入防护、projectPath 越界/symlink/
  未知服务/commit mismatch、search/callers/callees/impact/explore 结构化映射、未知/畸形响应 fail closed、
  production 不装配、development 装配、遥测/更新禁用、默认 verify 不依赖本机 CodeGraph、Java/Spring fixture 契约、
  真实 CodeGraph E2E 显式开关默认跳过（未安装固定版本报 NOT_EXECUTED）。
- 文档：README、ADR-002、local-environment.example.md、energy-platform-demo README、新增验收报告。
