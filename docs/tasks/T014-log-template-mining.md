# T014 — Log Template Mining (Drain3)
## Goal
把应用日志聚类为模板并抽取参数，作为运行时证据能力。
## Scope
- 新建 Drain3 sidecar（Python，标准库 http.server + 真实 drain3）：POST /api/v1/logs/parse、GET /api/v1/logs/templates。
- agent-common 增加 LogTemplateMinerClient 契约 + LogParseResult/LogTemplate/LogParameter DTO。
- agent-adapter-runtime 增加 Drain3LogTemplateMinerClient（Spring RestClient），远端 DTO 不泄漏到 Core。
- Toolset 增加 mine_log_templates 工具；InvestigationToolExecutor 分发到日志模板挖掘客户端。
## Do Not
- 不在 Java 侧重写 Drain 算法（用真实 Drain3）。
- 不引入 Knowledge/RAG/Vector DB。
## Acceptance
- sidecar 用真实 drain3 把相似日志聚类为模板并抽取参数（已验证）。
- Drain3LogTemplateMinerClient 覆盖 parseLogs / listTemplates。
- Toolset 含 mine_log_templates 且不含 execute_shell。
