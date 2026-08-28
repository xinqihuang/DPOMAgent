## Context

See `proposal.md` for motivation. The current main service-boundary specification already names DPOMAgent as Investigation authority, but historical Phase 1/5 changes, acceptance documents and implementations were produced against a later-abandoned DPOMBase-owned runtime. DPOMBase has now removed diagnosis, persistence and messaging modules. DPOMAgent contains substantial uncommitted work that must be characterized and preserved. Root ADR, phase documents and shared contracts are not in a Git repository after retirement of AISREPlatformGovernance, so clean-clone reproducibility is currently impossible.

## Goals / Non-Goals

**Goals:**

- Establish exactly one durable Diagnosis/Investigation authority in DPOMAgent.
- Preserve the accepted Phase 1A HTTP behavior while migrating DPOMAgent publication to Kafka.
- Restore Phase 1 and Phase 5 objective acceptance under the corrected ownership.
- Make every build, contract and platform status source reproducible from version-controlled repositories.
- Preserve DPOMBase evidence-only and AlarmChangeGuard mutation boundaries with executable guards.

**Non-Goals:**

- Moving evidence SDKs, cloud credentials or production mutation into DPOMAgent.
- Reintroducing diagnosis, reports, MySQL Investigation state or Kafka publication into DPOMBase.
- Automatically applying Phase 4 recommendations or alarm changes.
- Treating the blocked Phase 2 approved-model gate as successful without a real six-Judge run.

## Decisions

### 1. Reconstruct authority in DPOMAgent from current behavior and verified history

Characterize DPOMAgent's current dirty worktree before editing. Reuse compatible domain/persistence/outbox logic already present there; use deleted DPOMBase history only as a behavioral reference, not as a package-for-package copy. DPOMAgent owns domain transitions and transaction boundaries, while adapters remain replaceable.

Alternative: revert the latest DPOMBase refactor. Rejected because it violates the explicit evidence-only boundary and recreates two possible diagnosis owners.

### 2. Use one frozen canonical outbox record with HTTP and Kafka delivery adapters

DPOMAgent commits terminal diagnosis state and a publication intent in one local MySQL transaction. The intent contains the canonical event bytes/digest and stable identities. HTTP compatibility and Kafka publishers deliver the same frozen record. SRE routes both transports through one ingestion command and records transport observations separately from canonical content.

Alternative: generate Kafka events from HTTP payloads or SRE projections. Rejected because retries could change content and downstream state would become an alternate source authority.

### 3. Split report authority by profile, not by renderer

DPOMAgent builds and persists diagnosis-only canonical revisions from terminal Investigation facts. SRE consumes that immutable source contract, attaches its own exact evaluation lineage and persists evaluated canonical revisions. Renderers consume canonical reports and never query domain stores directly. DPOMBase only supplies referenced evidence artifacts.

Alternative: keep all report builders in SRE. Rejected for diagnosis-only authority because SRE would need to reconstruct or duplicate Investigation semantics.

### 4. Put producer contracts and platform governance sources in DPOMAgent Git

DPOMAgent will contain producer-owned schemas/fixtures/canonicalization vectors and the authoritative platform ADR/phase-status/acceptance index under repository-owned paths. SRE will use a pinned published artifact or repository-owned conformance fixtures with source version and SHA-256 provenance. No Maven build-helper source injection from `../contracts` remains. Repo-specific operational reports stay in their owning repositories.

Alternative: recreate a generic governance root repository. Rejected because the user explicitly retired AISREPlatformGovernance. Leaving files only under a machine-specific workspace root is also rejected because other computers cannot reproduce them.

### 5. Treat old acceptance as historical evidence, not current PASS

Phase 1 and Phase 5 status returns to in-progress during migration. New acceptance uses dedicated local MySQL schemas, Kafka 9092, controlled evidence fixtures, clean clones and exact Git commits. Phase 2 remains pending until its explicit approved-model gate succeeds; Phase 3/4 evidence is rerun after prerequisites are consistent.

### 6. Preserve rollback without dual authority

Kafka cutover rolls back to the compatibility HTTP delivery adapter while DPOMAgent remains authoritative. Report generation flags can be disabled without deleting immutable revisions. Schema rollback is guarded and only allowed for empty new structures; binary/feature rollback is preferred when history exists.

## Risks / Trade-offs

- [DPOMAgent has 63 uncommitted paths that may overlap the migration] → Capture an exact baseline, classify ownership and patch incrementally; never reset or discard user work.
- [Historical DPOMBase tests can give false confidence after code moved] → Rebuild architecture, persistence and transport tests in the owning repository and verify clean clones.
- [Dual HTTP/Kafka delivery can duplicate ingestion] → Freeze identical canonical content and prove idempotency/conflict parity through one SRE application port.
- [Moving contracts can break consumers] → Pin version/digest provenance, retain compatibility fixtures and remove sibling coupling only after consumer clean-clone tests pass.
- [Phase 5 report snapshots can drift across services] → Validate one canonical semantic model and cross-language digest vectors before renderer snapshots.
- [External model or cloud credentials may be unavailable] → Continue all offline/local infrastructure work; keep credential gates explicit and fail closed rather than substituting fake success.

## Migration Plan

1. Capture repository status, commit identities, active tools, schemas, endpoints and old acceptance claims; mark contradicted Phase 1/5 claims in-progress.
2. Establish DPOMAgent domain/persistence characterization, then complete missing Investigation, ToolUse, terminalization and bounded progress behavior.
3. Move/version producer contracts and platform ADR/status sources into DPOMAgent; make DPOMAgent and SRE clean-clone builds self-contained.
4. Implement DPOMAgent transactional outbox, Kafka publisher and compatibility HTTP adapter; unify SRE ingestion and prove parity/failure recovery.
5. Implement DPOMAgent diagnosis-only canonical reports and update SRE evaluated-report adapter/assembly; run schema, semantic, revision and renderer equivalence tests.
6. Verify DPOMBase evidence-only and AlarmChangeGuard mutation architecture gates across the workspace.
7. Execute dedicated MySQL/Kafka cross-service rehearsals, clean-clone builds, restart/replay/cutover/rollback scenarios and acceptance fixture diagnosis/report flow.
8. Publish corrected Phase 1/5 matrices and status, rerun Phase 2–4 prerequisite/regression gates, commit and push each owning repository.

Rollback disables new Kafka/report entry points, drains or expires leases, returns publication to HTTP compatibility and retains all authoritative DPOMAgent state, outbox records, SRE projections and report revisions.
