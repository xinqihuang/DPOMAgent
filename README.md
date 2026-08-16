# DPOMAgent — 双区域闭环故障诊断引擎

DPOMAgent 是**同一套诊断引擎**，通过两种部署 Profile 在**生产区域**与**研发区域**之间形成闭环：
不是两个互不相关的 Agent，也不是每个代码仓各部署一个 Agent。

## 定位

### 生产区域（production profile）
- `DPOMBaseMCPServer` 部署在客户/生产华为云环境，是**只读的华为云证据网关**，负责 AOM、CES、APM、LTS、CCE
  等工具访问与证据标准化；它不是诊断大脑，不承担 LLM 推理、RCA 编排或源码分析。
- `DPOMAgent production profile` 负责调查编排、时间线、证据关联、假设与置信度、结论或升级判断。
- 生产侧不要求原始源码；允许使用与发布版本匹配、经过边界裁剪的 `CodeGraph` 结果。
- 禁止 RAG/Embedding/Vector DB；禁止任意 Shell；禁止生产写操作；禁止自动执行修复或生成脚本；
  只允许人审批后的显式动作。

### 研发区域（development profile）
- 集中部署一套 `DPOMAgent development profile`，不为每个仓库部署独立 Agent。
- 配套 Repository Registry、`CodeGraph`（colbymchenry/codegraph，stdio MCP）、精确 release/commit 源码快照和研发侧 LLM。
- 接收生产侧证据包，校验完整性、版本和脱敏元数据后，结合准确源码进行最终 RCA。
- `CodeGraph`（`colbymchenry/codegraph`）是源码导航/结构化上下文，不包装成向量检索或 RAG。

### 跨区域交接
- OBS 是**受控证据传输通道**，不是知识库。
- 上传内容只能是版本化、限量、脱敏的 Diagnostic Evidence Package：告警、时间窗、拓扑/调用链、
  日志模板与代表样本、指标趋势摘要、CodeGraph 摘要、假设/矛盾/降级信息、服务/环境/release/commit、
  校验和与 schemaVersion。
- 禁止上传源码、AK/SK、Token、Cookie、原始大批量日志、无边界 dump。
- “满足升级条件”与“允许上传”分离：系统可以判断 `escalationEligible`，但 OBS 上传必须有显式 approval gate。

## 硬边界（Hard Boundaries）

- No RAG / Embedding / Vector DB。
- No arbitrary shell execution tool。
- No automatic production execution（诊断/修复脚本仅生成 Artifact，DPOMAgent 不执行）。
- No source / credential upload（证据包禁止源码、AK/SK、Token、Cookie、原始大批量日志、无边界 dump）。
- 单实例 Java Web；不做 Docker/K8s/Helm/Kafka/HA（Redis 仅用于缓存）。

## 技术栈

JDK21、Maven 3.9+、Spring Boot 3.4.5、Spring MVC + Virtual Threads、Spring AI 1.0.4、
Spring RestClient、MySQL + Flyway + JdbcClient、Jackson、JUnit5/Mockito/AssertJ。

## 模块

- `agent-common`：跨模块共享的内部 DTO、Port、枚举与异常。
- `agent-adapter`：LLM / Runtime / CodeGraph 适配器。
- `agent-core`：调查编排、假设、证据、工作区、工具、脚本、持久化、证据交接。
- `agent-web`：唯一可执行 jar（REST 控制器、配置、迁移、composition root）。

## 开发顺序

1. `AGENTS.md` → `CLAUDE.md` → `openspec/config.yaml` → 当前 Change → 一张 Task Card。
2. 测试先行，逐项实现并把 `tasks.md` 对应项更新为 `[x]`。
