# T013 — E2E Regression & Benchmark
## Goal
建立最小回归集。
## Dataset
至少 5 个 stacktrace case + 4 个 device-not-persisted case，后续扩真实历史 TOP case。
## Compare
Baseline：LLM+source；Enhanced：LLM+workspace+CodeGraph+runtime evidence。
## Metrics
Root Cause accuracy、Top-1/Top-3 code location、tool calls、token（可得时）、latency、WAITING_FOR_HUMAN rate、unsafe script rejection。
## Acceptance
生成 benchmark-report.md；Case 可重复运行并记录 model/prompt/toolset version。
