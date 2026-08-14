# T001 — Maven Java Web Skeleton
## Goal
创建 DPOMAgent 单实例 Java Web Maven 多模块骨架。
## Scope
parent POM：JDK21 / Spring Boot3.4.5 / Spring AI1.0.4。
modules：agent-common、agent-adapter/{llm,runtime,codegraph}、agent-core、agent-web。
Checkstyle 与 DPOMBaseMCPServer 对齐；AGENTS.md / CLAUDE.md / README.md；agent-web 唯一 executable jar；virtual threads。
## Do Not
Docker/Helm/K8s、Redis/Kafka、RAG/Embedding、正式业务实现。
## Acceptance
```bash
mvn clean verify
mvn -pl agent-web -am package
```
只有 agent-web 生成 executable jar。
