# Proposal: Add Log Evidence to Code Context

## Why

当前系统已经能够调用 Drain3 MCP、CodeGraphContext、Snapshot Workspace 和 LLM，但这些能力仍是彼此独立的工具。
海量日志被模板化后尚未形成可审计的日志证据，也没有稳定地把异常、logger、类、方法、固定日志文本等代码锚点连接到准确版本的代码图和事实源码。

本 Change 建立从日志到代码的受控证据管道，使 LLM 基于压缩、脱敏、带来源且绑定 release/commit 的 Evidence Bundle 形成和验证根因假设。

## What Changes

1. 对运行时日志进行有界输入、结构化前缀分离、脱敏和 Drain3 模板化；
2. 聚合模板频次、时间范围、严重级别、代表样本和参数分布；
3. 从模板和代表样本提取 Exception、logger、class、method、package、日志常量、HTTP path 等代码锚点；
4. 使用 CodeGraphContext 导航符号和调用关系，并读取同一 release/commit Snapshot 的事实源码；
5. 生成带 provenance 的 Evidence Bundle，交给现有调查循环和 LLM；
6. 建立独立 eval fixtures 和机器可读断言；
7. 跑通真实 Drain3，以及可显式启用的 Drain3 + CodeGraphContext + LLM E2E。

## Out of Scope

- Knowledge/RAG/Embedding/Vector DB；
- 任意 Shell 工具；
- 自动执行生产诊断或修复脚本；
- 自动修改源码或提交修复；
- CCE 写操作、Pod 重启或配置变更；
- 跨故障长期知识沉淀；
- 多 Agent 平台。

## Success Criteria

- 大量相似日志不会原样进入 LLM，而会形成有界、脱敏、可追溯的模板证据；
- 至少一个模板能够提取代码锚点，经 CodeGraphContext 导航后读取准确 commit 的源码；
- 根因结论同时引用日志证据与事实源码证据，否则只能 INCONCLUSIVE 或 WAITING_FOR_HUMAN；
- 默认构建不依赖真实外部服务，真实 E2E 可显式启用；
- 首批三个能源场景 fixture 可重复执行并产生机器可读结果。
