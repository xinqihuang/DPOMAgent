# Phase 2 — Curated Cases and Governed Evaluation Data

- Status: Implementation Complete / Acceptance Pending — approved-model six-Judge gate remains open
- Prerequisite: Phase 1 corrected four-core-service boundaries and compatibility cutover are accepted.
- Goal: convert Phase 1 evaluation projections into reviewable, versioned Incident Cases and reproducible Dataset Versions.

## Scope

### Incident Case Versioning

- Create immutable Incident Case Versions from normalized incident, evidence, topology, reconstructed fault source, and fault chain facts.
- Preserve source lineage and distinguish factual source fields, reviewer-supplied ground truth, and derived fields.
- A correction creates a new version; prior versions remain queryable and cannot be rewritten.

### Gold Review Workflow

- Define Bronze as machine-curated, Silver as validated for structural completeness, and Gold as human-approved ground truth.
- Require authenticated reviewer identity, decision, reason, timestamp, and compared version.
- Reject stale review decisions and prohibit self-promotion by background jobs.

### DeepEval Multi Judge

- Expand the fixed catalog to RootCause, FaultSource, FaultChain, EvidenceGrounding, TaskCompletion, and InvestigationQuality.
- Freeze judge, prompt, model, threshold, input, and output schema versions per evaluation run.
- Preserve individual judge outcomes; missing, invalid, or unavailable required judges produce INCOMPLETE.

### Judge Human Agreement

- Capture bounded human labels against the same immutable case version and rubric.
- Calculate sample count, agreement rate, confusion data, and an approved chance-corrected agreement metric.
- Prevent a judge version from becoming release-critical until minimum sample and agreement policies pass.

### Dataset Lifecycle

- Build immutable Dataset Versions from explicit Incident Case Version membership.
- Lifecycle: DRAFT -> REVIEW -> APPROVED -> ACTIVE -> DEPRECATED -> ARCHIVED.
- Freeze membership, tier mix, selection policy, schema versions, and content digest at approval.
- Dataset mutation creates a new version and never changes an evaluation run already in progress.

## Processing model

Spring Batch jobs perform bounded ODS-to-DWD normalization, DWD-to-DWS case construction, and dataset materialization. Jobs are restartable and idempotent. MySQL owns state and lineage; OBS contains controlled large evidence artifacts. No distributed stream or lakehouse engine is introduced.

## Exit criteria

- [ ] One incident produces two immutable case versions with visible lineage.
- [ ] Bronze-to-Silver-to-Gold review is authenticated, audited, and stale-write safe.
- [ ] The six required semantic judges execute through the fixed DeepEval contract.
- [ ] Judge-human agreement is reproducible for a frozen sample and rubric version.
- [ ] An approved Dataset Version has immutable membership and digest.
- [ ] A replay over that Dataset Version is restartable and produces authoritative per-case results.
- [ ] Retention, redaction, capacity, and rollback procedures are documented and tested.

## Not in scope

Automated release blocking, capability-gap recommendations, autonomous code/prompt changes, and production remediation.
