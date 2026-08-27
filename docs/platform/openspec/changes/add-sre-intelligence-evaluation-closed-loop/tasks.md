# Tasks: SRE Intelligence Data & Evaluation Closed Loop

This is an implementation backlog. Phase 1A items are evidence-backed compatibility facts. Phase 1B and later unchecked work require a scoped apply change and do not become implemented merely because they appear here.

## 1. Phase 1A compatibility baseline protection

- [x] 1.1 Preserve the versioned Diagnosis Event contract, canonical digest, idempotent receipt, conflict quarantine, and READY Eval Case projection.
- [x] 1.2 Preserve the fixed Java Rule Judge, stateless semantic-judge call, categorical PASS/FAIL/INCOMPLETE aggregation, and persisted replay lineage.
- [x] 1.3 Preserve Phase 1A acceptance evidence for real HTTP, MySQL 8, restart, duplicate delivery, fake model, and approved real model.
- [ ] 1.4 Add characterization coverage around the DPOMAgent-to-DPOMBase ownership migration before changing runtime behavior.

## 2. Phase 1B cross-service contracts and migration foundation

- [ ] 2.1 Define the Phase 1B Diagnosis Event, Evidence Manifest, progress event, and Judge Result schemas with positive and negative fixtures.
- [ ] 2.2 Define DPOMBaseMCPServer as source authority and document Kafka topic, partition, ordering, retention, retry, dead-letter/quarantine, and compatibility rules.
- [ ] 2.3 Route Kafka and Phase 1A HTTP transports through one ingestion application port and prove identical idempotency/conflict semantics.
- [ ] 2.4 Plan Investigation Runtime ownership/data cutover with compatibility, rollback, dual-path validation, and retirement criteria.
- [ ] 2.5 Add architecture tests forbidding cross-service database access, SRE/DeepEval Huawei credentials, DeepEval persistence, and production write capabilities.
- [ ] 2.6 Implement durable Kafka receipt, schema validation, bounded payload handling, canonical digest, ordering cursor, quarantine, and append-only audit through the same application port as HTTP.

## 3. Phase 2 incident data layers and evidence

- [ ] 3.1 Define ODS/DWD/DWS/ADS tables and reviewed versioned SQL release artifacts for MySQL without adding an application migration framework.
- [ ] 3.2 Implement evidence metadata and OBS artifact integrity checks, allow-listing, retention, redaction, and broken-reference states.
- [ ] 3.3 Implement versioned DWD normalization for incident identity, topology, code context, evidence, fault source, and fault-chain edges.
- [ ] 3.4 Add restartable, idempotent, capacity-bounded Spring Batch jobs and operational job controls.

## 4. Phase 2 Incident Case and Gold review

- [ ] 4.1 Implement immutable Incident Case identity/version, content digest, provenance, and supersession lineage.
- [ ] 4.2 Implement deterministic Bronze construction and validation reason codes.
- [ ] 4.3 Implement Silver structural/evidence validation without silently repairing source facts.
- [ ] 4.4 Implement authenticated Gold approve/reject/request-change workflow with optimistic concurrency and append-only review audit.
- [ ] 4.5 Add case history/query APIs and bounded SSE/operator progress that exclude evidence bodies and secrets.

## 5. Phase 2 Dataset lifecycle

- [ ] 5.1 Implement immutable Dataset Version membership using exact Incident Case Versions and a canonical cohort digest.
- [ ] 5.2 Implement DRAFT, REVIEW, APPROVED, ACTIVE, DEPRECATED, and ARCHIVED transitions with authorization and audit.
- [ ] 5.3 Implement deterministic selection policy, tier quotas, split/leakage checks, retention, and deprecation behavior.
- [ ] 5.4 Implement restartable dataset materialization and prove that later case changes cannot mutate prior datasets or active runs.

## 6. Phase 2 multi-judge replay and agreement

- [ ] 6.1 Add fixed versioned DeepEval definitions for RootCause, FaultSource, FaultChain, EvidenceGrounding, TaskCompletion, and InvestigationQuality.
- [ ] 6.2 Extend contracts and SRE orchestration for six independent semantic results with bounded concurrency, timeout, cost, retry, and fail-closed outcomes.
- [ ] 6.3 Implement Replay Plan preparation, frozen component/input versions, per-case work records, leasing, restart recovery, and authoritative reports.
- [ ] 6.4 Capture human labels against immutable case/rubric versions and calculate reproducible agreement snapshots.
- [ ] 6.5 Enforce minimum sample and agreement policy before a judge version becomes release-critical.
- [ ] 6.6 Verify Phase 2 end-to-end with non-production Kafka, MySQL, OBS fixture, batch restart, DeepEval fake/approved model, and replay parity.

## 7. Phase 3 failure attribution and capability gaps

- [ ] 7.1 Define and approve a bounded, versioned failure taxonomy with observed-fact and inferred-attribution separation.
- [ ] 7.2 Implement immutable attribution records, confidence, supporting/contradicting evidence, and human correction history.
- [ ] 7.3 Implement comparable-cohort capability-gap aggregation with minimum sample and data-quality policies.
- [ ] 7.4 Implement evidence-backed advisory recommendations with target, expected benefit, risk, validation dataset, and rollback criteria.

## 8. Phase 3 Release Gate

- [ ] 8.1 Define a fixed versioned Gate Policy catalog covering baseline compatibility, required datasets/suites/judges, agreement, freshness, samples, and thresholds.
- [ ] 8.2 Implement immutable ALLOW/BLOCK decisions where missing, stale, incomplete, unavailable, incompatible, or insufficient evidence always BLOCKS.
- [ ] 8.3 Implement authenticated, time-bounded waivers as separate records that never overwrite the original gate decision.
- [ ] 8.4 Add report-only rollout, decision review, notification/outbox integration, enforcement kill switch, and rollback runbook.
- [ ] 8.5 Verify candidate-versus-baseline regression, incomplete dependency, incompatible cohort, stale evidence, and waiver expiry scenarios.

## 9. Phase 4 Future Improvement Agent

- [ ] 9.1 Define a proposal-only agent contract that consumes confirmed gaps and cannot deploy, approve, waive, or access production writes.
- [ ] 9.2 Implement immutable candidate artifacts and sandboxed replay requests with cost/time/tool budgets and kill switches.
- [ ] 9.3 Produce an improvement dossier with rationale, candidate reference, evaluation evidence, risk, and rollback.
- [ ] 9.4 Require human adoption/rejection and preserve the full proposal decision history.

## 10. Verification and handoff

- [ ] 10.1 Run unit, architecture, contract, batch restart, Kafka redelivery, MySQL, OBS, cross-service HTTP, security, and capacity suites for each phase.
- [ ] 10.2 Prove forbidden technologies and forbidden dependency surfaces are absent.
- [ ] 10.3 Publish phase acceptance reports, schema/API snapshots, operational runbooks, retention policy, and exact rollback evidence.
- [ ] 10.4 Update phase status only after every exit criterion has objective repository evidence.
