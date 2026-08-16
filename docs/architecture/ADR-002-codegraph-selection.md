# ADR-002：代码图选型切换为 CodeGraph

## 状态

已接受并实现。迁移由独立 OpenSpec Change `replace-codegraphcontext-with-codegraph` 完成：stdio MCP 接入、受控进程、Repository Registry（含 projectPath 反查校验）、development-only 装配、遥测禁用与版本化文本解析。

## 决策

DPOMAgent 的代码图标准实现切换为 [`colbymchenry/codegraph`](https://github.com/colbymchenry/codegraph)，统一名称为 **CodeGraph**。不再把 CodeGraphContext 作为目标技术选型。

## 理由

- CodeGraph 使用本地 SQLite/WAL 和自包含运行时，适合研发区域的单仓或多仓离线索引。
- 原生支持 Java，并能识别 Spring `@RequestMapping`、`@GetMapping`、`@PostMapping` 等路由关系。
- 提供 symbol search、callers、callees、impact、files 和综合 `codegraph_explore` 能力。
- 支持 Windows、Linux、macOS 的 x64/arm64 发布包，便于研发区和受控构建环境部署。
- 项目发布与问题修复更活跃，且许可证为 MIT。

## 集成边界

- CodeGraph 只部署在研发区域或受控代码索引区域，不部署到无源码的生产诊断节点。
- DPOMAgent 继续依赖内部 `CodeGraphClient` DTO；CodeGraph 的 MCP DTO 不得泄漏到 core。
- 图用于导航和候选关系，最终源码事实仍必须绑定精确 release/commit snapshot。
- 跨区域只允许传输有界、脱敏的图摘要，禁止传输源码正文和 `.codegraph` 数据库。
- 禁用遥测：`CODEGRAPH_TELEMETRY=0` 或 `DO_NOT_TRACK=1`。
- 生产采用固定、校验过的版本和离线安装包，不在运行时自动升级或下载。
- CodeGraph 默认 MCP transport 为 stdio；不得继续复用旧 CodeGraphContext 的 SSE 工具协议。

## 后续 Change

创建 `replace-codegraphcontext-with-codegraph`，完成：

1. CodeGraph stdio MCP transport 和生命周期管理。
2. `codegraph_search/node/callers/callees/impact/explore/status` 到内部 DTO 的映射。
3. Repository Registry 与 projectPath、release、commit snapshot 的确定性绑定。
4. Java/Spring fixture 的符号、调用链、路由和影响面验收。
5. 删除非历史文件中的 CodeGraphContext/CGC 配置、文档和测试命名。
6. 保持 No RAG、No arbitrary shell、No production source access 边界。
