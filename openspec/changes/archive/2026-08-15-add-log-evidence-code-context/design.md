# Design: Log Evidence to Code Context

## 1. Pipeline

```text
RuntimeEvidenceClient
        |
        v
Bounded Log Intake
        |
        v
Prefix Split + Redaction
        |
        v
Drain3 MCP
        |
        v
LogEvidenceAggregator
        |
        v
CodeAnchorExtractor
        |
        +---- no anchors ----> Evidence Bundle (log only)
        |
        v
CodeGraphClient
        |
        v
Snapshot CodeWorkspace
        |
        v
EvidenceBundle
        |
        v
Investigation / LLM
```

## 2. Bounded Log Intake

所有日志输入必须绑定 service、environment、timeRange，并限制：

- 最大日志行数；
- 最大总字节数；
- 单行最大字节数；
- 最大模板数；
- 每个模板最大样本数和参数值数。

超过限制时必须截断并记录 truncation metadata，不得静默丢弃，也不得把未截断原始日志交给 LLM。

## 3. Normalization and Redaction

timestamp、host、pod、level、logger、traceId 等结构化前缀与 message 分离。仅 message 送入 Drain3，结构化字段保留用于聚合。

在持久化和进入 LLM 前对 token、password、authorization、手机号、邮箱、IP、deviceId/tenantId 等按策略脱敏。允许保留稳定 hash 以支持同值关联，但不得反向恢复。

## 4. Log Evidence

每个模板形成一个 LogEvidence：

- evidenceId / clusterId / template；
- count / firstSeen / lastSeen；
- severity distribution；
- representative samples；
- redacted parameter distribution；
- service/environment/release/commit/timeRange；
- traceIds 或 artifact refs；
- truncated / miner configuration version。

不在 MySQL 保存海量原始日志，仅保存摘要、来源引用和必要的代表样本。

## 5. Code Anchor Extraction

V1 使用确定性规则，不引入 embedding：

- Java exception fully-qualified name；
- stack frame class/method/file/line；
- logger/package/class name；
- 固定日志文本；
- HTTP method/path；
- mapper/repository/SQL statement id；
- 显式类名和方法名。

每个 anchor 必须包含 type、value、sourceEvidenceId、confidence 和 extraction rule version。

## 6. Graph Navigation and Source Verification

CodeGraphContext 仅负责候选导航。每个候选必须绑定 Incident 的 release/commit Snapshot。高置信候选通过 CodeWorkspace 读取事实源码；Snapshot 不匹配或不 READY 时不得宣称代码根因。

读取源码继续遵循根目录限制、路径防逃逸和字节/行数限制。

## 7. Evidence Bundle

Evidence Bundle 是本轮 LLM 输入的唯一新增载体，包含：

- incident/version identity；
- log evidence summaries；
- extracted anchors；
- graph candidates；
- verified source excerpts；
- contradictions、missing evidence、truncation 和 degradation；
- 每项 evidence 的 provenance。

Bundle 必须有总字节/token 预算并执行确定性排序：异常和 ERROR、时间邻近、频次突变、跨证据关联、源码已验证优先。

## 8. Failure and Degradation

- Drain3 不可用：保留有界代表日志并标记 LOG_MINER_UNAVAILABLE；
- CodeGraph 不可用：允许在 Snapshot 内按锚点受控搜索；
- Snapshot 不匹配：只输出日志证据，不输出代码根因；
- LLM 不可用：证据可保存并恢复调查；
- 无锚点或证据不足：进入 INCONCLUSIVE/WAITING_FOR_HUMAN。

所有降级必须写入 InvestigationStep/Observation/Audit。

## 9. Evaluation

首批 fixture：

- E01 device transaction rollback；
- E03 telemetry partial batch loss；
- E05 downstream timeout and retry storm。

每个 fixture 包含 incident、logs、expected、代码版本和触发说明。默认测试使用 FakeModelClient、Fake Drain3/CodeGraph 或录制响应；真实 E2E 由环境变量显式启用。

## 10. Boundaries

不增加 RAG、向量数据库、任意 Shell、生产写工具或自动执行路径。DPOMAgent 仍为单实例 Java Web；外部能力继续通过 Adapter 隔离。
