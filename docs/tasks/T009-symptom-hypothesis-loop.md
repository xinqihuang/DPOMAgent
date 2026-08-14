# T009 — Symptom-driven Hypothesis Loop
## Goal
无异常堆栈时根据业务症状形成调查路径。
## First Symptom
“创建设备成功，但数据库没有数据”。
## Dimensions
request arrival、response semantics、business branch、Service→Repository、SQL、transaction、datasource/schema/tenant、async、read-side、release regression。
## Rules
2~5 个候选 Hypothesis；下一工具优先区分竞争假设；证据更新状态；无证据不得 VALIDATED；允许 INCONCLUSIVE。
## Acceptance
至少多假设、使用 runtime/code 工具、invalidate 错误假设、证据不足进入 WAITING_FOR_HUMAN。
