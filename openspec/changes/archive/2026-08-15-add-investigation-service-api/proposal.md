# Proposal: Add Investigation Service API

## Why
三个 Change 已打通证据/诊断链路，但主链路主要由测试直接调用，缺可供 SRE/上层平台使用的产品化 Java Web API。

## What Changes
1. 版本化 REST API /api/v1/investigations（提交/查询摘要与状态/时间线/证据/结论）。
2. 应用编排层（Controller 不直接拼依赖，新增 application service）。
3. 有界异步执行 + 幂等 + 启动 reconciliation。
4. API DTO 与领域对象分离；idempotency/execution 元数据持久化（新 Flyway migration）。
5. 健康/就绪信息；日志带 investigationId/runId。

## Out of Scope
UI、连接/修改真实 CCE、RAG/Embedding/Vector DB、任意 shell、自动执行生产脚本、V2。

## Success Criteria
提交→调查→查询状态→查看证据与结论全链路可用；默认有界异步；安全只读边界；test-fixtures compile 与 mvn clean verify 通过。
