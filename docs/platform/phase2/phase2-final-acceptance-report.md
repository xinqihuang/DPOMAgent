# Phase 2 Final Acceptance Report

- Acceptance date: 2026-08-27
- Scope: governed Incident Case curation, Dataset lifecycle, fixed six-Judge replay, and Judge-human agreement.
- Decision: **ACCEPTED** — all implementation, real-infrastructure and approved-model gates passed.

## Delivered outcome

Phase 1 accepted events can now be projected into immutable, lineage-preserving Incident Case Versions; validated and human-approved as Gold; frozen into immutable Dataset Versions; replayed through one deterministic rule and six independently persisted semantic Judges; and compared with immutable human-label revisions through reproducible raw agreement and Cohen's kappa snapshots.

MySQL owns authoritative state and history. Controlled evidence bodies remain outside MySQL behind a bounded allow-listed artifact port. Every mutation/worker capability is production-safe default-off. Required Judge absence, timeout, invalid output, dependency failure, or exhausted budget is explicit `INCOMPLETE`, never inferred success.

## Objective acceptance evidence

- H2 covers insertion, uniqueness, optimistic concurrency, cursor pagination, transactions, immutable history reconstruction, restart parity, lease generations, authoritative result uniqueness, frozen labels, agreement confusion, and eligibility denial.
- Real MySQL covers the same persistence contract against port 3306.
- The non-production end-to-end contract uses local Kafka, real MySQL, a verified allow-listed OBS fixture, an injected expired lease/restart, the DeepEval fake HTTP adapter, seven authoritative results, and reconstructed PASS report parity.
- Cross-service tests execute six independent Judge calls and retain explicit timeout/invalid-output evidence.
- Approved-model execution remains separately gated by explicit non-production environment and approval identity; it cannot run accidentally.
- On 2026-08-27 the fixed six-Judge gate ran against the approved DeepSeek-compatible `deepseek-chat` profile. All
  six calls returned contract-valid `FAIL` decisions with pinned component/prompt/rubric/schema versions; the gate
  passed 1 test with 0 failures/errors/skips. A `FAIL` Judge decision is valid evidence and is not infrastructure failure.
- Runtime credentials were injected only into the interactive process environment and were cleared afterward; no
  credential, raw provider response or semantic input body was retained in the acceptance artifact.
- Security tests prove credentials, evidence bodies, prompts, ground-truth bodies, raw model output, and arbitrary exceptions do not enter bounded APIs, logs, metrics, audits, or progress events.

## Operational conclusion

Retention, redaction, capacity, batch/replay recovery, feature enable/disable, and rollback are defined in `operations-runbook.md`. Database rollback remains guarded and is not appropriate when immutable Phase 2 history must be retained; feature/binary rollback is the default.

Phase 3 release blocking, failure attribution, and capability recommendations remain out of scope. Phase 2 eligibility decisions are calibration evidence only and cannot make a Phase 3 release decision.
