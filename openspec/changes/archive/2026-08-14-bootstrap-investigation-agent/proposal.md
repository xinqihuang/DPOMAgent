# Proposal: Bootstrap DPOM Investigation Agent

## Why
真实 SRE 故障不总有异常堆栈。例如“资产管理界面创建设备成功，但数据库无记录”，可能是请求未到后端、
业务分支提前返回、Repository 未调用、SQL 被捕获、事务回滚、datasource/schema/tenant 错误、
异步链路失败、读取侧过滤或发布回归。

因此项目目标不是“异常堆栈解释器”，而是症状驱动、假设驱动的 Investigation Agent。

## What Changes
新增研发侧单实例 Java Web `DPOMAgent`，提供：
1. Incident/Investigation 生命周期；
2. LLM Tool Calling；
3. Hypothesis/Observation/Conclusion；
4. DPOMBaseMCPServer Runtime Evidence Client；
5. DPOMCodeGraphService Client；
6. 受控 Code Snapshot Workspace 搜索/源码阅读；
7. 证据不足时生成 Shell/Python/SQL 诊断脚本；
8. 生成 Mitigation Script Artifact，但不自动执行；
9. 全链路 Audit。

## Boundary
- DPOMAgent：LLM 推理和调查编排。
- DPOMBaseMCPServer：运行时确定性证据。
- DPOMCodeGraphService：代码 Snapshot、Git/CGC 导航。
- Knowledge/RAG：本期不做。

## Out of Scope
Knowledge/RAG、Vector DB、Embedding、自动知识沉淀、多 Agent 平台、Docker/K8s/Helm、
Redis/Kafka、分布式锁、高可用、SSH 到生产、生产自动执行、任意 Shell 工具。

## Success Criteria
至少跑通：
1. 明确异常堆栈 -> 正确 Snapshot -> 源码/调用图 -> RCA；
2. “创建设备成功但未落库” -> 多假设 -> 取证 -> 必要时脚本补证据 -> RCA。
每次结论必须引用 Observation/Evidence。
