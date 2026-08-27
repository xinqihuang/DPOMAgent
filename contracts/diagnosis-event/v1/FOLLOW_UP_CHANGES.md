# Phase 1 后续 OpenSpec Changes

本文件只定义后续变更边界和依赖，不在当前架构/契约变更中实现运行时代码。每个 change 必须独立生成 proposal、
spec、design 和 tasks，并继续遵守 `docs/platform/ADR.md` 与 Diagnosis Event v1。

## 第一组：垂直切片前置能力

### `add-dpomagent-diagnosis-event-outbox`

范围：DPOMAgent 在调查完成事务中持久化 canonical Diagnosis Event；实现 Outbox 状态、相同身份重试、限界退避、
可观测失败和人工 Replay 入口。

前置：

- `define-ai-sre-evaluation-boundaries` 已实施并归档；
- Diagnosis Event v1 Schema、正反 fixtures 和离线 validator 已发布；
- 明确 DPOMAgent 哪个状态转换首次生成 `investigation.completed`。

明确不含：Kafka、SRE Intelligence 表、DeepEval、Nightly Eval。

### `add-sre-intelligence-diagnosis-ingestion`

范围：建立 SRE Intelligence Service 最小 Java 服务；实现幂等 HTTP 摄取、canonical content hash、序号 gap 隔离、
不可变事件保存和 Eval Case 最小投影；适配一个现有 DPOMAgent 确定性 Regression Rule。

前置：

- Diagnosis Event v1 契约已冻结；
- Outbox change 已确定 delivery acknowledgement 和稳定错误码；
- 明确 MySQL/Flyway/MyBatis 基线及 Artifact resolver Port。

明确不含：完整 ODS/DWD/DWS/ADS、Bronze/Silver/Gold 晋级、Nightly 调度和 Release Gate。

### `add-deepeval-semantic-judge`

范围：建立无状态 Python/FastAPI DeepEval Service；定义版本化 Judge Request/Result；先实现一个
Evidence Grounding 或 Root Cause Judge，并覆盖超时、失败、非法输入和无凭据日志。

前置：

- Diagnosis Event Provenance 字段和 Artifact 校验语义已冻结；
- SRE Intelligence 定义 Judge client Port、超时预算及失败结果模型；
- Judge Prompt 具有稳定 identifier/version，结果不直接形成 Release Gate。

明确不含：Dataset 持久化、聚合、调度、生产证据访问和 Release Gate。

## 第二组：端到端垂直切片

### `add-ai-sre-evaluation-vertical-slice`

范围：选择 DPOMAgent 现有真实回归案例，串联 Base evidence → Agent persisted investigation → Outbox → SRE
Intelligence ingestion/Rule Judge → DeepEval Judge → versioned report，并证明从持久化 Artifact 重放不依赖原 LLM
会话。

前置：

- 上述三个第一组 changes 均已完成各自契约测试；
- 统一 incidentId、investigationId、runId 和 idempotencyKey 可端到端追踪；
- 正反契约 fixtures 同时在 Java 与 Python consumer tests 中通过；
- 垂直切片验收清单中的每个阶段都有机器可读证据。

## 第三组：必须等待垂直切片成功

以下 change 不是垂直切片前置条件。只有 `add-ai-sre-evaluation-vertical-slice` 完成、回放稳定且边界未被推翻后
才能启动：

1. `add-eval-case-bronze-silver-gold`：Case tier、人工校订、Gold 审核发布和不可变版本历史。
2. `add-nightly-evaluation-suites`：Capability、Regression、Rolling Production Suite 的定时、断点续跑和趋势。
3. `add-evaluation-failure-classification`：从独立 Rule/Judge 结果生成机器可读失败分类，禁止只留总分。
4. `add-evaluation-release-gate`：基线比较、关键 Case 零退化、阈值、阻断、人工豁免和审计。

依赖关系：

```text
outbox -----------+
ingestion/rule ---+--> vertical-slice --> case-tiering --> nightly-eval
deepeval-judge ---+                          |               |
                                             +--> failure ----+--> release-gate
```

如垂直切片发现事件身份、Provenance、Artifact 或所有权设计需要修改，必须先更新并重新验证当前中立契约，不能在
后续服务内部增加私有旁路字段。
