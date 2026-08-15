# Proposal: Add Real Diagnostic Regression Suite

## Why

add-log-evidence-code-context 已通过验收，但仅有 E01 单案例跑通过真实 Drain3 + CodeGraphContext + DeepSeek 联合诊断；T013 的真实 E2E Regression & Benchmark 尚未完整完成。当前缺一个统一、可重复、可量化、防答案泄露的多案例诊断回归套件。

## What Changes

1. 三个代表性真实诊断案例（E01 设备创建事务回滚、E03 遥测批处理部分丢失、E05 下游超时重试风暴），每个案例含 incident/logs/expected/固定版本源码 workspace/recorded Drain3/recorded CodeGraph/README。
2. fixture 防答案泄露校验：symptom、日志正文、文件名与模型提示不得直接告诉模型 rootCauseId；expected.json 仅供评估器，严禁进入 LLM prompt/EvidenceBundle。
3. 真实联合回归链路（raw logs → redaction → Drain3 → LogEvidence → Code Anchors → CGC → snapshot/source → EvidenceBundle → DeepSeek → Conclusion → ConclusionEvaluator），显式开关启用，真实模式不使用 recorded/fake 客户端。
4. 可重复 Benchmark Runner：一次执行全部案例，每案例独立结果，输出 diagnostic-regression.json（atomic write）。
5. 指标：caseCount/executedCount/passedCount/failedCount、rootCauseAccuracy、evidenceGroundingRate、completionRate、inconclusiveRate、per-case latency 与 p50/p95；总体 passed 仅在全部 mandatory 案例执行且全部断言通过。
6. 防作弊负向测试：expected 不入 prompt、symptom/logs 泄露 rootCauseId 校验失败、错误 rootCauseId 失败、缺引用失败、部分未执行总体失败、旧结果不误用。
7. test-fixtures/energy-platform-demo：最小但真实可编译的 Spring 风格能源平台示例源码（asset-service/telemetry-service/gateway-service）。

## Out of Scope

Knowledge/RAG/Embedding/Vector DB；任意 Shell 工具；自动执行诊断/缓解脚本；连接或修改真实 CCE 生产环境；第二个常驻 Web 控制面；V2 能力。

## Success Criteria

- 三案例真实联合回归可重复执行，每案例断言 investigationStatus/resultType/rootCauseId/证据引用/VERIFIED source/expectedSymbols。
- diagnostic-regression.json 由真实执行生成，总体 passed 不因跳过/缺失记为通过。
- 防答案泄露与防作弊负向测试通过。
- 默认 mvn clean verify 成功；外部 E2E 显式开关启用。
