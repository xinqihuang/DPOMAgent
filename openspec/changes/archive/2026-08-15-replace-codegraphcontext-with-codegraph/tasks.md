# Tasks

## 1. agent-common port 与 DTO

- [x] 1.1 测试先行：新增 `RepositoryRegistry` port 与 `RegisteredRepository`/`CommitMismatchException`；`CodeGraphClient` 增加兼容默认方法 `findImpact` 并清理 CodeGraphContext 注释

## 2. CodeGraph stdio 客户端与解析器（agent-adapter-codegraph）

- [x] 2.1 测试先行：版本化 `CodeGraphResponseParser`（FORMAT_VERSION=v1.5.0）解析 search/callers/callees/impact/node/explore 文本，fixture contract test + 未知/畸形 fail closed
- [x] 2.2 测试先行：受控进程 `CodeGraphProcessParameters`（固定参数 serve --mcp + 固定遥测禁用 env）与 `CodeGraphVersionValidator`（可执行文件不存在/版本不匹配 fail）
- [x] 2.3 测试先行：`CodeGraphStdioClient` 用 stdio transport + 固定 tool list，实现 resolveSnapshot（委托 RepositoryRegistry）与 findSymbol/findCallers/findCallees/findCallChain/findClassHierarchy/findImpact 映射，timeout/transport closed 映射异常
- [x] 2.4 删除 `McpCodeGraphClient`（SSE），重写 `CodeGraphAdapterConfiguration` 为 development 条件装配 + stdio transport

## 3. Repository Registry 与配置装配（agent-web）

- [x] 3.1 测试先行：`ConfigRepositoryRegistry` 读配置做 serviceCode+commit 确定映射（未知服务/commit mismatch fail closed）与 real-path containment（越界/symlink escape 拒绝）
- [x] 3.2 application.yml 移除 `dpom.codegraph.mcp-base-url`，新增 `dpom.codegraph.enabled/executable-path/version/mcp-tools` 与 `dpom.repositories` 模板
- [x] 3.3 测试先行：profile 隔离——production 无 active CodeGraph stdio adapter/进程/源码访问，仅 fail-closed 禁用态 port；development 装配前校验 executable 存在与版本匹配；遥测/更新检查禁用

## 4. 边界与回归

- [x] 4.1 测试先行：禁止命令/参数/环境注入；projectPath 越界、symlink escape、未知 service、commit mismatch
- [x] 4.2 Java/Spring energy-platform-demo fixture 契约：类/方法、caller/callee、Spring route、impact
- [x] 4.3 真实 CodeGraph E2E：显式 `DPOM_CODEGRAPH_E2E=true` 启用、默认跳过；未安装固定版本报 NOT_EXECUTED

## 5. 文档与清理

- [x] 5.1 清理非历史文件 CodeGraphContext/CGC 表述（Java 注释/测试名/变量、metrics/health 名称、fixtures、E2E）
- [x] 5.2 更新 README、ADR-002、local-environment.example.md、energy-platform-demo README
- [x] 5.3 输出 docs/replace-codegraphcontext-with-codegraph-acceptance-report.md 并跑完整验收（validate --strict / mvn clean verify / fixture clean compile / 残留扫描）

