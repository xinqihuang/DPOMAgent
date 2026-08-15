# Tasks: Add Real Diagnostic Regression Suite

- [x] **T201 — Fixture answer-leak validation**（依赖：无）
  - 测试先行：FixtureValidator 校验 symptom/logs 不泄露 rootCauseId；expected 不入 prompt。
- [x] **T202 — test-fixtures/energy-platform-demo**（依赖：无）
  - 三个可编译 Spring 风格示例源码 + README（索引与回归说明）。
- [x] **T203 — Benchmark runner + metrics**（依赖：T201）
  - 统一 Runner、per-case 结果、指标计算、diagnostic-regression.json atomic write。
- [x] **T204 — E01/E03/E05 真实联合回归 E2E**（依赖：T203）
  - 显式开关，真实 Drain3/CGC/DeepSeek，逐案例断言。
- [x] **T205 — Anti-cheat negative tests**（依赖：T201,T203）
  - 错误 rootCauseId、缺引用、部分未执行、旧结果不误用。
- [x] **T206 — Reports + repeatable entry**（依赖：T203,T204）
  - benchmark-report.md（机器结果）、acceptance report、可重复入口。
- [x] **T207 — Boundary + final acceptance**（依赖：T201-T206）
  - mvn clean verify；边界审计；显式真实回归；最终报告。
