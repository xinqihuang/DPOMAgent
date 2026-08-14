# T004 — Investigation State Machine
## Goal
实现可恢复、有限步数调查运行时。
## States
CREATED→SCOPING→RESEARCHING→FORMING_HYPOTHESES→VALIDATING→SYNTHESIZING→COMPLETED，
以及 WAITING_FOR_HUMAN / INCONCLUSIVE / FAILED / CANCELLED。
## Core
InvestigationCoordinator, InvestigationBudget, StepRecorder, HypothesisService, ObservationService。
## Rules
每轮一个 bounded tool action；检查 maxSteps/maxToolCalls/maxDuration/maxNoProgressRounds。
## Acceptance
正常完成、budget 截断、WAITING_FOR_HUMAN、restart/resume、否定证据保留。
