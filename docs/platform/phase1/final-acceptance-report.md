# Phase 1 Final Acceptance Report

- Acceptance date: 2026-08-28
- Decision: **ACCEPTED**
- Authority: DPOMAgent owns Investigation/Diagnosis; DPOMBaseMCPServer is read-only evidence tooling; SRE Intelligence owns evaluation ingestion/control; DeepEval is stateless.
- Transport: DPOMAgent HTTP compatibility outbox and Kafka outbox share one frozen canonical event and rollback-safe state machine.
- Component heads: DPOMAgent `e56a53f`, DPOMBaseMCPServer `fd08e6d`, SRE Intelligence `9b8c242`, DeepEval `fc57486`, HuaweiCloudAlarmChangeGuard `a8bbef4`.

## Final decision

Phase 1 is accepted under the corrected four-core-service boundary. DPOMAgent remains the authoritative, durable and restartable source of Incident/Investigation/Run/Step, ToolUse, Observation, Hypothesis, Conclusion, progress and diagnosis-source facts. DPOMBaseMCPServer exposes bounded read-only Huawei Cloud evidence tools and contains no model, diagnosis state, report authority, Kafka producer or alarm mutation capability.

The Phase 1B migration changes only DPOMAgent-to-SRE transport. State is committed before publication; HTTP and Kafka use the same frozen event identity/digest and SRE ingestion policy; duplicate, conflict, ordering gap, quarantine, retry, replay and rollback behavior is evidence-backed. The compatibility HTTP adapter remains the documented rollback path and does not reverse authority.

## Objective evidence

| Gate | Result |
|---|---|
| DPOMAgent clean-clone full Maven reactor | PASS — 536 tests, 0 failures/errors, 50 gated skips; executable JAR produced |
| DPOMBaseMCPServer full Maven reactor | PASS — 431 tests, 0 failures/errors, 1 gated skip |
| SRE Intelligence full Maven reactor | PASS — 367 tests, 0 failures/errors, 6 gated skips |
| DeepEval lint/type/test | PASS — Ruff, strict mypy and 68 tests |
| DPOMAgent authority on real MySQL 8 | PASS — 20 tests covering uniqueness, locking, rollback, history, reconstruction and restart-like reads |
| Local Kafka 4.3.1 durability | PASS — marker survived broker restart and was consumed from retained storage |
| DPOMAgent real Kafka publication | PASS — Diagnosis Event and progress contracts acknowledged by the external broker |
| SRE Kafka/MySQL ingestion | PASS — duplicate/conflict/order/quarantine/replay and HTTP rollback parity |
| Service/credential boundaries | PASS — five-service workspace verifier and architecture guards |
| Strict OpenSpec validation | PASS |

Detailed command-level evidence is retained in the archived realignment change, especially `evidence/phase7-runtime-contracts.md`, `evidence/phase6-service-boundaries.md`, `evidence/isolated-contract-builds.md`, and `evidence/phase4-authority-outbox-and-transport.md`.

## External mutation acceptance separation

The guarded CES and AOM non-production disable/readback/restore checks passed and both rules finished in their original enabled state. APM did not pass: token-authenticated read prechecks returned HTTP 403 `apm2.00000004`, no PUT was sent, and the rule was unchanged. That independent provider/IAM acceptance is tracked by active change `validate-apm-alarm-rule-suppression-recovery`; it is not presented as Phase 1 runtime evidence and does not weaken the accepted service boundary.

## Operational conclusion

Kafka admission/publication remains explicit and rollback-safe. Broker or consumer failure cannot change Investigation authority, and rollback selects the HTTP compatibility adapter over the same durable outbox record. Credentials, raw evidence bodies, prompts and unrestricted model output are excluded from persisted events, progress, logs and acceptance artifacts.
