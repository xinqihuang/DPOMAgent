# Design: Real Diagnostic Regression Suite

## 1. Fixture Layout

```text
evals/cases/<case>/
├── incident.json          # serviceCode/environment/release/commit/symptom（symptom 不得泄露 rootCauseId）
├── logs.txt               # 原始日志（stack frame 允许，禁止把答案当标签注入）
├── expected.json          # rootCauseId/expectedSymbols/requiredEvidenceTypes/forbiddenConclusions（仅供评估器）
├── recorded-drain3.json   # 离线录制响应
├── recorded-codegraph.json
├── workspace/             # 与 commit 对应的固定版本源码
└── README.md
```

## 2. Fixture Answer-Leak Validation

FixtureValidator 在离线评测与真实回归前校验：
- symptom 不得包含 rootCauseId 或 expectedSymbols。
- logs 每条：若包含 rootCauseId，仅允许出现在 stack frame（`at FQN.method(File.java:line)`）中；禁止作为标签/提示直接注入。
- expected.json 只被评估器读取，不进入 prompt/EvidenceBundle。

## 3. Benchmark Runner

统一 Runner 遍历 mandatory 案例，逐案例跑真实链路，每个案例独立结果；单案例失败不阻断其他案例；用临时文件 + atomic move 写 `agent-web/target/e2e-results/diagnostic-regression.json`，运行前删除旧结果。

## 4. Metrics

从 per-case 结果计算：caseCount/executedCount/passedCount/failedCount、rootCauseAccuracy（actual==expected 的比例）、evidenceGroundingRate（同时含 LOG + VERIFIED SOURCE 引用的比例）、completionRate（COMPLETED 比例）、inconclusiveRate（INCONCLUSIVE 比例）、latency p50/p95。总体 passed = 全部 mandatory 案例 executed 且 passed。

## 5. Real Combined Regression

复用 add-log-evidence-code-context 的 LogEvidenceService + ConclusionEvaluator + InvestigationCoordinator；真实模式注入真实 Drain3/CGC/DeepSeek，不注入 recorded/fake 客户端。

## 6. test-fixtures/energy-platform-demo

提供 asset-service、telemetry-service、gateway-service 三个最小可编译 Spring 风格示例，源码与 eval workspace/commit 映射一致；README 说明 CodeGraphContext 索引与回归运行方式。

## 7. Boundaries

不引入 RAG/Embedding/Vector DB、任意 Shell、自动生产执行、真实 CCE 连接；DPOMAgent 仍单实例 Java Web。
