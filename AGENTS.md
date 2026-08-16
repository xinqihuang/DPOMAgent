# AGENTS.md
## Project
DPOMAgent：同一套诊断引擎、两种部署 Profile（production/development）、双区域闭环。
## Before Coding
先读 openspec/config.yaml、当前 Change、当前 docs/tasks/TNN。
## Hard Boundaries
- No Knowledge/RAG/Vector DB/Embedding.
- No Docker/K8s/Helm/Kafka.（Redis 已解除边界，见 openspec/config.yaml）
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
