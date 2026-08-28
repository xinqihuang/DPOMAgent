# Phase 1 Requirement-to-Evidence Matrix

Acceptance baseline: 2026-08-28 corrected DPOMAgent-authority architecture and the exact component heads recorded in `final-acceptance-report.md`.

| Requirement | Objective evidence | Result |
|---|---|---|
| DPOMAgent is the durable Investigation/Diagnosis authority | Realignment evidence `dpomagent-authority-persistence-implementation.md`, `dpomagent-authority-h2-mysql-contracts.md`, and architecture tests | PASS |
| State commits before publication | `dpomagent-terminal-source-atomicity.md`; outbox transaction/failure tests | PASS |
| Canonical Diagnosis Event is versioned, bounded and portable | Producer-owned contracts, fixtures, canonical vectors and `dpomagent-producer-contract-migration.md` | PASS |
| HTTP/Kafka share canonical identity and ingestion semantics | `phase4-authority-outbox-and-transport.md`; local Kafka/MySQL contracts | PASS |
| Duplicate/conflict/order/quarantine behavior fails safely | DPOMAgent publisher and SRE unified-ingestion integration suites | PASS |
| Retry, lease recovery and replay survive restart | Broker restart, expired lease, uncertain-send and replay evidence in `phase7-runtime-contracts.md` | PASS |
| HTTP rollback preserves authority and history | `docs/runbooks/phase1b-kafka-cutover-and-http-rollback.md`; executed HTTP rollback parity contract | PASS |
| Progress is bounded, authenticated and replayable | DPOMAgent progress/SSE API, authorization, pagination, replay and redaction tests | PASS |
| ToolUse/evidence references are auditable and secret-safe | `dpomagent-tooluse-security.md`; redaction and bounded-body tests | PASS |
| DPOMBase is evidence-only | DPOMBase evidence-only architecture suite and `phase6-service-boundaries.md` | PASS |
| SRE owns evaluation ingestion without cross-database access | HTTP/Kafka unified command and workspace-boundary verifier | PASS |
| DeepEval is stateless and returns individual Judge results only | DeepEval architecture/API tests and clean repository validation | PASS |
| Clean-clone and repository portability | `isolated-contract-builds.md`, `governance-migration-and-portability.md`, workspace verifier | PASS |
| Real infrastructure gate | MySQL 8 contracts, Kafka 4.3.1 publication/ingestion and broker restart evidence | PASS |

The APM alarm-rule mutation live check is intentionally absent from this matrix: it is neither a Diagnosis runtime requirement nor a substitute for evidence-only/mutation-service architecture enforcement. Its unpassed provider/IAM acceptance is tracked independently by `openspec/changes/validate-apm-alarm-rule-suppression-recovery`.
