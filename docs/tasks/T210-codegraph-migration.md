# T210 — Replace CodeGraphContext with CodeGraph stdio MCP

## Goal

把代码图接入从旧 CodeGraphContext 的 MCP-over-SSE 迁移到 CodeGraph（colbymchenry/codegraph）官方 stdio MCP：
受控进程、Repository Registry 确定映射、development-only 装配、遥测禁用、版本化文本解析 fail closed，
内部 CodeGraphClient port 与 Symbol/CallStep/ClassHierarchy/CodeSnapshot DTO 保持不变。

## Acceptance

- 默认 `mvn clean verify` 不依赖本机 CodeGraph、不启动 stdio 子进程，BUILD SUCCESS。
- CodeGraph 经 Java MCP SDK stdio transport（`codegraph serve --mcp`）接入，固定工具 `CODEGRAPH_MCP_TOOLS`，
  参数/环境由代码固定，不经 shell，无命令/参数/环境注入面。
- projectPath 由 Repository Registry（serviceCode+release/commit）解析并做 real-path containment；
  未知服务/commit mismatch/越界/symlink escape 一律 fail closed，无「选第一个仓库」。
- production profile 不装配 CodeGraph、无 stdio 子进程、无源码访问；development profile 正确装配。
- `CODEGRAPH_TELEMETRY=0`、`DO_NOT_TRACK=1`、`CODEGRAPH_NO_UPDATE_CHECK=1` 固定注入，无运行时 update check/自动升级/下载。
- findSymbol→search/node、findCallers→callers、findCallees→callees、findCallChain→explore、findClassHierarchy→explore/node、
  impact→内部 port 有界摘要；文本经版本化解析器解析，未知/畸形 fail closed，不可解析时安全降级并记录原因。
- 非历史文件不再把 CodeGraphContext/CGC 描述为当前实现；真实 CodeGraph E2E 显式开关默认跳过，未安装固定版本报 NOT_EXECUTED。
- `openspec validate replace-codegraphcontext-with-codegraph --strict` 通过。

