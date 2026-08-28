# Phase 1 — Four-Core-Service Diagnosis-to-Evaluation Foundation

- Status: In Progress — authority realignment
- Previous acceptance: 2026-08-25, retained as historical evidence for the superseded DPOMBase-owned boundary
- Active change: `openspec/changes/realign-phase1-phase5-to-dpomagent-authority`
- Goal: deliver DPOMAgent diagnosis authority, DPOMBase evidence tools, SRE evaluation control and stateless DeepEval
  with a bounded, rollback-safe HTTP-to-Kafka compatibility window.

The Phase 1 implementation and local Kafka/MySQL acceptance criteria below are complete. Final platform status
publication remains open because the encompassing realignment change still has one deferred external service-boundary
gate: the authorized APM rule disable/readback/restore check. The 2026-08-28 token-authenticated precheck failed with
`apm2.00000004` before any PUT was sent; this does not invalidate the Phase 1 runtime evidence and is not a PASS for
the remaining cross-service gate.

## Milestone 1A — Delivered compatibility baseline

- Versioned Diagnosis Event contract with correlation IDs, component provenance, canonical digest, and evidence references.
- Durable, idempotent SRE ingestion with conflict quarantine and no cross-service database access.
- READY Eval Case projection from persisted diagnosis facts.
- Fixed deterministic Java Rule Judge.
- Stateless DeepEval invocation through a versioned, bounded HTTP contract.
- Fixed `phase1-diagnostic-quality@1.0.0` suite with one rule judge and two semantic judges.
- PASS, FAIL, and INCOMPLETE aggregation that never infers success from missing or unavailable results.
- Persisted replay with lineage and frozen input/version digests.
- Operator APIs, audit, Micrometer/Actuator health, capacity visibility, and rollback-by-disable.

## Acceptance evidence

The repository acceptance report records successful real HTTP ingestion, idempotent redelivery, restart behavior, persisted replay, real MySQL 8 checks, fake-model cross-service checks, and an approved non-production real-model run:

`SREIntelligenceService/docs/evaluation-suite-acceptance-report.md`

The Phase 1A report remains the historical compatibility baseline. Phase 1B implementation and final
acceptance evidence are recorded in `../phase1b/phase1b-acceptance-report.md` and
`../phase1b/evidence/phase1-final-acceptance-2026-08-25.json`.

## Milestone 1B — Historical implementation, current revalidation in progress

- Keep authoritative online Diagnosis and Investigation Runtime in DPOMAgent.
- Publish immutable diagnosis events and bounded progress from DPOMAgent after source state is durable.
- Add Kafka delivery while keeping the Phase 1A HTTP endpoint as a compatibility adapter.
- Route Kafka and HTTP through one SRE ingestion application port and prove equivalent behavior.
- Provide bounded SSE diagnosis progress from DPOMAgent to Portal.
- Cut over DPOMAgent's HTTP Outbox transport to Kafka only with characterization coverage, data compatibility, replay validation and rollback; diagnosis ownership does not move.

## Exit criteria

- [x] Source event is authenticated, versioned, bounded, and durably acknowledged.
- [x] Duplicate delivery is idempotent and digest conflict fails closed.
- [x] Eval Case retains source correlation and evidence references.
- [x] Rule and semantic results are stored independently.
- [x] Aggregate outcome is deterministic and auditable.
- [x] Replay works without the original LLM conversation.
- [x] Service and credential boundaries are verified.
- [x] Scenario-by-scenario acceptance evidence exists, including real local Kafka and cutover/rollback.
- [x] DPOMAgent owns durable, restartable Diagnosis and Investigation state under the corrected boundary.
- [x] DPOMAgent Kafka publication occurs only after authoritative diagnosis state is durable; real MySQL and broker gates pass.
- [x] Kafka and compatibility HTTP have equivalent idempotency, conflict, ordering, replay, and observability semantics.
- [x] DPOMAgent SSE exposes safe diagnosis progress without evidence bodies, prompts, model output, or credentials.
- [x] HTTP-to-Kafka cutover, rollback and default-off admission/publication are objectively verified without moving diagnosis ownership.

## Deferred to later phases

Incident Case history, Bronze/Silver/Gold governance, full judge catalog, human-agreement calibration, Dataset lifecycle, failure attribution, capability-gap analysis, Release Gate, and Improvement Agent.
