## 1. Baseline and Contradiction Control

- [x] 1.1 Capture exact branch, commit and worktree inventories for DPOMAgent, DPOMBaseMCPServer, SREIntelligenceService, DeepEvalService and HuaweiCloudAlarmChangeGuard without modifying pre-existing changes.
- [x] 1.2 Characterize DPOMAgent's current Investigation, ToolUse, HTTP outbox, persistence, progress and report behavior with focused tests before migration edits.
- [x] 1.3 Record the active cross-service contract/tool/API inventory and identify every Phase 1/5 claim that still assigns diagnosis, Kafka or report ownership to DPOMBase.
- [x] 1.4 Change portable Phase 1 and Phase 5 status sources to In Progress with links to this realignment change; retain prior acceptance as explicitly historical evidence.

## 2. DPOMAgent Investigation Authority

- [x] 2.1 Complete framework-neutral Incident, Investigation, Run, Step, Observation, Hypothesis, Conclusion, budget, ToolUse and audit domain behavior in DPOMAgent with deterministic identities and state invariants.
- [x] 2.2 Add reviewed MySQL deployment/verification/safe-rollback SQL plus MyBatis adapters for authoritative Investigation state, append-only histories and optimistic concurrency without application-managed DDL.
- [x] 2.3 Add H2 and real-MySQL tests for insert/uniqueness, optimistic locking, transaction rollback, exact-history reconstruction, interruption/resume and concurrent terminalization.
- [x] 2.4 Persist bounded ToolUse decisions/results and immutable DPOMBase evidence references while rejecting credentials, raw envelopes, unrestricted bodies and fabricated missing evidence.
- [x] 2.5 Implement atomic terminal diagnosis source projection and publication-intent creation; prove failed persistence or invariant validation publishes nothing.
- [x] 2.6 Implement authenticated bounded progress/SSE and immutable diagnosis-source APIs in DPOMAgent with authorization, pagination, replay and redaction tests.

## 3. Portable Contracts and Governance Sources

- [x] 3.1 Move producer-owned Diagnosis Event/Progress, Evidence Manifest and diagnostic-report schemas, semantic rules, fixtures and canonicalization vectors into version-controlled DPOMAgent paths with version and SHA-256 provenance.
- [ ] 3.2 Remove DPOMAgent build/test dependence on root or sibling contract directories and prove its full build in an isolated clean clone.
- [ ] 3.3 Replace SRE `../contracts` build-helper/test-resource coupling with repository-owned pinned conformance inputs or a versioned artifact and prove its full build in an isolated clean clone.
- [x] 3.4 Move the authoritative platform ADR, phase roadmaps, acceptance index and this OpenSpec change into version-controlled DPOMAgent documentation without recreating AISREPlatformGovernance.
- [x] 3.5 Add a portability verifier that fails when active builds or authoritative documentation reference machine-specific workspace-root paths or unversioned parent/sibling sources.

## 4. DPOMAgent Kafka Outbox and SRE Unified Ingestion

- [ ] 4.1 Implement DPOMAgent transactional outbox rows, canonical payload/digest freezing, aggregate sequencing, leases, attempt history, idempotent acknowledgement and replay state.
- [ ] 4.2 Implement default-off bounded Kafka Diagnosis Event/Progress publishers with readiness, capacity, retry, conflict and secret-safe telemetry.
- [ ] 4.3 Retain the Phase 1A authenticated HTTP delivery adapter over the same frozen outbox record and make transport selection explicit and rollback-safe.
- [ ] 4.4 Route SRE HTTP and Kafka adapters through one ingestion application command while preserving transport observations separately from canonical domain content.
- [ ] 4.5 Add contract and integration tests for HTTP/Kafka acknowledgement, idempotency, conflicting duplicates, per-Investigation ordering gaps, quarantine, replay and equivalent projections.
- [ ] 4.6 Add restart/failure tests for state commit before publication, broker outage, uncertain send, expired lease, retry exhaustion and publisher/consumer restart without duplicate authority.
- [ ] 4.7 Update cutover/rollback runbooks for DPOMAgent-owned authority, default-off admission epochs, compatibility window, reconciliation and rollback to HTTP without ownership reversal.

## 5. Phase 5 Report Ownership and Projections

- [ ] 5.1 Publish the bounded canonical diagnostic-report contract with diagnosis-only/evaluated profiles, semantic invariants, positive/negative fixtures and cross-language digest vectors.
- [ ] 5.2 Implement DPOMAgent diagnosis-only source adapter, canonical builder and immutable persistence from terminal Investigation facts without LLM-authored report fields.
- [ ] 5.3 Implement diagnosis-only request idempotency, optimistic revision creation, supersession/recovery lineage, exact-history queries, transaction rollback and H2/real-MySQL tests.
- [ ] 5.4 Update SRE evaluated-report assembly to consume an immutable versioned DPOMAgent diagnosis source and attach exact Eval Case/Dataset/Replay/Suite/individual-Judge lineage without cross-database access.
- [ ] 5.5 Verify canonical, Markdown, Portal, HTML and PDF semantic equivalence, template/report digests, authorization, bounds and secret/evidence-body redaction.
- [ ] 5.6 Reproduce APM alarm `16557989` as a non-production golden fixture with structurally distinct Eden/CodeCache evidence, limitations and immutable recovery revision.

## 6. Service Boundary Enforcement

- [ ] 6.1 Re-run and extend DPOMBase architecture/tool-catalog guards to reject diagnosis, model, report, Kafka producer, Investigation persistence and alarm mutation responsibilities.
- [ ] 6.2 Complete HuaweiCloudAlarmChangeGuard architecture and contract tests proving it exclusively owns APM/CES/AOM state mutations with approval, audit and rollback boundaries.
- [ ] 6.3 Execute authorized non-production APM, CES and AOM disable/mask then restore checks where suitable test resources exist, preserving exact before/after provider responses without credentials.
- [ ] 6.4 Add a workspace boundary verifier covering dependency direction, database ownership, cloud credentials, mutation tools and forbidden cross-service source imports.

## 7. Cross-Phase Verification and Delivery

- [ ] 7.1 Run full offline builds, architecture/security/redaction suites and strict OpenSpec validation for all five affected services from clean clones.
- [ ] 7.2 Run DPOMAgent Investigation/report real-MySQL contracts on a fresh dedicated schema at local MySQL 3306, including restart, transaction and immutable-history evidence.
- [ ] 7.3 Run the DPOMAgent-to-Kafka-to-SRE end-to-end flow on local Kafka 9092 and real MySQL, including duplicate/conflict/order, broker restart, replay and HTTP rollback parity.
- [ ] 7.4 Run Phase 5 diagnosis-only and evaluated-report cross-service acceptance with deterministic replay and renderer equivalence against exact component commits.
- [ ] 7.5 Re-run Phase 2 local fake-model infrastructure and Phase 3/4 regression suites; keep Phase 2 pending until its separate approved-model six-Judge gate objectively passes.
- [ ] 7.6 Publish corrected Phase 1/5 requirement matrices, acceptance reports and status only after every gate passes, and reconcile Phase 2–4 prerequisite language with actual evidence.
- [ ] 7.7 Review all final diffs for unrelated/user-owned changes and secret literals, commit and push each owning repository, then verify clean worktrees and matching remote heads.
