# DPOMAgent 诊断回归基准报告

> 由真实机器结果生成（agent-web/target/e2e-results/diagnostic-regression.json），非手写宣称。
> 模型 deepseek-v4-pro、prompt/toolset/rule=v1、miner=drain3-mcp-0.9。

## 案例
| caseId | 根因 | actual rootCauseId | 状态 |
|---|---|---|---|
| E01-device-transaction-rollback | AssetRepository.insert | AssetRepository.insert | COMPLETED |
| E03-telemetry-partial-batch-loss | BatchPublisher.flush | BatchPublisher.flush | COMPLETED |
| E05-downstream-timeout-retry-storm | DownstreamClient.call | DownstreamClient.call | COMPLETED |

## 指标
| 指标 | 值 |
|---|---|
| caseCount | 3 |
| executedCount | 3 |
| passedCount | 3 |
| failedCount | 0 |
| rootCauseAccuracy | 1.0 |
| evidenceGroundingRate | 1.0 |
| completionRate | 1.0 |
| inconclusiveRate | 0.0 |
| latency p50 | ~32s |
| latency p95 | ~49s |
| overallPassed | true |

## 复现
```
mvn clean verify
DPOM_E2E_FULL=true DEEPSEEK_API_KEY=... mvn -pl agent-web -am test -Dtest=DiagnosticRegressionE2ETest
```
