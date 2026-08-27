# AGENTS.md
## Project
DPOMAgent 是 Investigation/Diagnosis 的唯一权威来源。迁移期保留 production/development
Profile、既有 Investigation 数据与 Diagnosis Event v1 HTTP Outbox；Phase 1B 迁移的是
DPOMAgent 到 SRE 的 HTTP Outbox 到 Kafka，不迁移 Agent 或诊断所有权。
## Workspace Authority
- `docs/platform/ADR.md` 高于本仓库 README、旧 ADR、OpenSpec 和任务卡。
- 本仓库拥有 Diagnosis Event/Progress 的 Producer contract；Kafka 仅允许出现在事件传输
  adapter/composition 边界，不得泄漏到 core domain。
- 迁移期间不得静默改写、迁移或删除既有调查记录；兼容行为必须先有 characterization coverage。
- HTTP 兼容窗口结束时只退休 HTTP adapter；DPOMAgent 和 Investigation/Diagnosis 权威不退休。
## Before Coding
先读 openspec/config.yaml、当前 Change、当前 docs/tasks/TNN。
## Hard Boundaries
- No Knowledge/RAG/Vector DB/Embedding.
- No Docker/K8s/Helm. Kafka client 仅限 Diagnosis Event/Progress 传输 adapter。
- No arbitrary shell execution tool.
- 生产诊断/修复脚本仅生成 Artifact，DPOMAgent 不执行。
- LLM/Runtime/CodeGraph DTO 不泄漏到 core。
- Workspace 访问限制在 Snapshot 根目录。
## Java
JDK21；Spring Boot3.4.5；Spring MVC；no Lombok/WebFlux/Guava/commons-lang3；
Logger=LOG；中文 Javadoc；method <=50 lines；tests first。
## Local Environment
本地中间件凭据（MySQL/Redis）只保存在本地的 docs/local-environment.md（已 gitignore）；
仓库内仅有脱敏模板 docs/local-environment.example.md。切换/验证持久化前先读它。
