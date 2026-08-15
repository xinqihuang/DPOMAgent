# add-real-diagnostic-regression-suite 验收报告

日期：2026-08-15
Change：add-real-diagnostic-regression-suite（未归档，等待独立验收）

## 任务状态
- T201 fixture 防答案泄露校验 [x]（FixtureValidatorTest 4）
- T202 test-fixtures/energy-platform-demo [x]（3 服务源码 + README）
- T203 Benchmark runner + metrics [x]（BenchmarkMetricsTest 2 + BenchmarkResultWriterTest 1）
- T204 三案例真实联合回归 E2E [x]（DiagnosticRegressionE2ETest，真实执行通过）
- T205 防作弊负向测试 [x]（FixtureValidatorTest + ConclusionEvaluatorTest + BenchmarkMetricsTest + BenchmarkResultWriterTest）
- T206 报告 + 可重复入口 [x]（本报告 + benchmark-report.md + README 命令）
- T207 边界 + 最终验收 [x]（mvn clean verify）

## 真实三案例回归（已执行）
| caseId | status | actual rootCauseId | expected |
|---|---|---|---|
| E01 | COMPLETED | AssetRepository.insert | AssetRepository.insert |
| E03 | COMPLETED | BatchPublisher.flush | BatchPublisher.flush |
| E05 | COMPLETED | DownstreamClient.call | DownstreamClient.call |

## 指标（来自 diagnostic-regression.json，mtime 2026/8/15 1:11:53）
- caseCount=3, executedCount=3, passedCount=3, failedCount=0
- rootCauseAccuracy=1.0, evidenceGroundingRate=1.0, completionRate=1.0, inconclusiveRate=0.0
- latency p50=32311ms, p95=48877ms, overallPassed=true

## 防作弊证明
- expected.json 不入 prompt（ExpectedNotInPromptTest）
- 错误 rootCauseId 失败（ConclusionEvaluatorTest.wrongRootCauseIdFails）
- symptom/log 泄露 rootCauseId 失败（FixtureValidatorTest）
- 缺 LOG/VERIFIED SOURCE 引用失败（ConclusionEvaluatorTest.missingReferencesFail）
- 部分未执行总体失败（BenchmarkMetricsTest.overallPassedRequiresAllExecutedAndPassed）
- 旧结果不误用（BenchmarkResultWriterTest.staleResultIsDeletedAndReplaced）

## 修改文件
openspec/changes/add-real-diagnostic-regression-suite/{proposal,design,tasks,specs}.md；
agent-core/.../eval/{FixtureValidator,BenchmarkCaseResult,BenchmarkMetrics,BenchmarkResultWriter}.java 及测试；
agent-web/.../DiagnosticRegressionE2ETest.java；test-fixtures/energy-platform-demo/**；docs/{benchmark-report,real-diagnostic-regression-acceptance-report}.md；evals/cases/E03 fixture 修正。

## 测试统计
mvn clean verify：BUILD SUCCESS，0 failure，111 测试，5 跳过（adapter 19 + core 63 + web 29）。test-fixtures/energy-platform-demo 也已 \`mvn -f .../pom.xml compile\` 编译通过（可编译）。
跳过：CodeWorkspace symlink（Windows）；Log4jStacktraceE2ETest/CombinedE2ETest/Drain3IntegrationE2ETest/DiagnosticRegressionE2ETest（真实外部服务默认跳过；后两者已显式执行通过）。

## 风险
真实 E2E 依赖本机 Drain3/CGC/DeepSeek key；缺少任一条件时应标记 NOT_EXECUTED（本机三者可用，已真实执行）。

## 结论
边界保持（No RAG/Embedding/Vector DB、无 arbitrary shell、无自动生产执行、单实例 Java Web）。本 Change 不归档，停止等待独立验收。
