# Phase 5 Acceptance Report

- Accepted: 2026-08-26
- Contract: `diagnostic-report/1.0.0`
- Template: `diagnostic-report-standard@1.0.0`
- DPOMBaseMCPServer: `0.0.1-SNAPSHOT`
- SRE Intelligence Service: `0.1.0-SNAPSHOT`
- Historical decision: **PASS** for the superseded DPOMBase-owned diagnosis profile
- Current decision: **REOPENED / IN PROGRESS** under `openspec/changes/realign-phase1-phase5-to-dpomagent-authority`

This report is retained as historical evidence. Its DPOMBase diagnosis-only ownership and test counts no longer describe
the active implementation after DPOMBase became evidence-only, so it MUST NOT be used as current Phase 5 completion proof.

## Objective results

| Verification | Result |
|---|---|
| Offline shared contract | `PHASE5_CONTRACT_STATUS=PASS valid=7 invalid=8` |
| SRE full reactor | BUILD SUCCESS; 352 tests, 0 failures, 0 errors, 6 explicit environment-gated skips |
| DPOMBase full reactor | BUILD SUCCESS; 501 tests, 0 failures, 0 errors, 0 skips; Checkstyle 0 violations |
| Real MySQL Phase 5 persistence | `PHASE5_MYSQL_CONTRACT_STATUS=EXECUTED outcome=PASS` |
| Real Kafka/MySQL cross-service flow | `PHASE5_E2E_CONTRACT_STATUS=EXECUTED kafka=PASS mysql=PASS judges=7 report=PASS` |

The real persistence contract exercised insert, request/revision uniqueness, optimistic revision protection, cursor pagination, transactional rollback and immutable history reconstruction against local MySQL. The cross-service acceptance consumed the shared diagnosis event through local Kafka, recovered an expired Judge lease, persisted seven individual Judge outcomes, finalized Phase 2 replay, generated the evaluated report and proved digest-identical replay.

## Semantic acceptance

- Canonical JSON is authoritative and excludes raw evidence bodies, credentials, prompts and unrestricted model output.
- Both profiles retain incident, investigation, run, target, evidence and component lineage.
- Completeness, conclusion disposition and evaluation outcome remain independent.
- Unsupported versions, digest mismatches, confirmed conclusions without evidence and PASS missing a required Judge kind fail closed.
- Multiple individual Judges may contribute to one normalized kind; `judgeResultId` remains unique and all seven Phase 2 results are retained.
- Markdown, Portal, HTML and deterministic PDF export originate from the same normalized view and preserve status/digest semantics.
- Published rows are immutable; later facts create a linked revision with an explicit bounded reason.

## Operational decision

Generation and rendering remain default-off. Rollout requires explicit non-production enablement and separate metadata/render/evidence authorization. Renderer failure does not affect canonical authority. Rollback disables generation/rendering first and retains immutable reports; reviewed SQL refuses unsafe removal while data remains.

No unresolved Phase 5 acceptance gap remains. Environment-gated SRE tests are intentionally skipped by the ordinary offline reactor and are covered separately by the executed local MySQL and Kafka acceptance runs above.

## Cross-phase audit

Phase 1, Phase 3 and Phase 4 remain accepted, and Phase 5 projects their immutable lineage without changing their ownership. Phase 2 implementation and fake-model/real Kafka/MySQL evidence are present, but its own roadmap intentionally remains `Planned`: the separate approved-provider six-Judge gate last failed closed on provider HTTP 401 and tasks 9.2/9.5 remain unchecked. That pre-existing external acceptance dependency does not weaken Phase 5's executed seven-result persistence and report-projection acceptance, and it has not been relabeled as success.
