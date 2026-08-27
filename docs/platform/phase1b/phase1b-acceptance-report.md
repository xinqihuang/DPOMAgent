# Phase 1B acceptance report

Status: Historical acceptance for the superseded DPOMBase-owned authority boundary. Phase 1 is currently In Progress under
`openspec/changes/realign-phase1-phase5-to-dpomagent-authority`; this report remains immutable historical evidence and is not
the current completion decision.
No production environment was mutated.

## Delivered topology

DPOMBaseMCPServer owns the durable Investigation runtime, service-local MySQL state, immutable Diagnosis Event
and Progress publication, and authenticated Portal REST/SSE. SRE owns one HTTP/Kafka ingestion policy,
quarantine/reconciliation, Eval Case projection and evaluation control. DeepEval remains stateless model
execution. DPOMAgent is the historical compatibility owner and has an explicit retired profile that disables
new admission and publication without deleting historical rows.

## Verification

| Target | Result |
|---|---|
| DPOMBase offline Maven verify | 493 tests, 0 failures/errors/skips; Checkstyle and architecture gates pass |
| DPOMAgent offline Maven verify | 458 tests, 0 failures/errors, 28 explicit gated skips; external MySQL adds 15 passing tests |
| SRE offline/profile reports | 148 tests, 0 failures/errors, 4 explicit external skips; external Kafka/MySQL and cross-service HTTP gates pass separately |
| DeepEval | 24 pytest pass; Ruff pass; strict mypy pass |
| Shared contracts | `PHASE1B_CONTRACTS=PASS event=2/12 evidence=1/7 progress=1/6` |
| Workspace boundary scan | `PHASE1_BOUNDARY_SCAN=PASS`, 132 neutral DPOM files, 239 SRE production files, 14 DeepEval production files, 2 write-tool gates |
| DPOMBase real MySQL 8.0.46 | `MYSQL_CONTRACT_STATUS=EXECUTED`, schema version 1 `READY` |
| SRE real MySQL + embedded Kafka | test-fixture contract passed; this is not real-broker acceptance |
| Local Kafka 4.3.1 (`127.0.0.1:9092`) | external Kafka + MySQL acceptance passed: redelivery, two-partition ordering, gaps, poison acknowledgement, cross-transport race, quarantine, immutable replay, capacity exhaustion and recovery |
| Local authority cutover/rollback | active epoch Kafka intake, MySQL counts/digest, readiness, invalid retired authority rejection, and HTTP v1 rollback passed; 4 tests, 0 failures/errors/skips |
| SRE deployment SQL | `SRE_DEPLOYMENT_SQL_VERIFY=PASS` |
| SRE → DeepEval real HTTP | pass/fail, timeout/retry, history/version and invalid-output cases pass against local fake model |
| OpenSpec | `openspec validate complete-phase1-three-service-convergence --strict` passes |

## Release assets

- Contracts and topic snapshots: `contracts/diagnosis-event/v2`, `contracts/evidence-manifest/v1`,
  `contracts/diagnosis-progress/v1`, and `contracts/kafka`.
- DPOMBase SQL: `agentic-persistence/src/main/resources/db/deployment/phase1b`.
- SRE SQL: `sre-web/src/main/resources/db/deployment/phase1b`, including guarded reverse-order rollback.
- API snapshot: `docs/phase1b/snapshots/progress-api-v1.md`.
- Operations: `authority-cutover-runbook.md`, `progress-capacity-retention-runbook.md`, producer/consumer
  reports, and `evidence/cutover-rehearsal-2026-08-25.json`.
- Traceability: `phase1b-requirement-evidence-matrix.md`.

## Completion audit

The final matrix is recorded in `evidence/phase1-final-acceptance-2026-08-25.json`. The status document,
requirement matrix, OpenSpec task list, machine-readable evidence, and strict validation were audited after
the final runs; stale pending claims were removed and credential values were not persisted.

Credentials supplied for verification were injected only into process environment variables. They are absent
from source, logs, reports, and committed configuration. Real model execution was not required for ingestion;
the approved cross-service acceptance used DeepEval's deterministic local fake mode.
