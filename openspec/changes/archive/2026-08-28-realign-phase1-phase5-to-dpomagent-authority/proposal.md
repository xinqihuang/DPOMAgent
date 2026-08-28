## Why

Phase 1 and Phase 5 acceptance artifacts still describe DPOMBaseMCPServer as the authoritative Diagnosis/Investigation runtime, even though the accepted architecture keeps that authority in DPOMAgent and now enforces DPOMBase as an evidence-only tool service. The implementation, contracts, portable documentation and acceptance evidence must be realigned before the platform can honestly claim Phase 1–5 completion.

## What Changes

- **BREAKING**: Remove the retired DPOMBase-owned Investigation, progress, diagnostic-report and Diagnosis Event producer assumptions from Phase 1/5 contracts, documentation and consumers.
- Make DPOMAgent the sole durable owner of Incident/Investigation/Run/Step/Observation/Hypothesis/Conclusion state, ToolUse decisions, diagnosis-only reports and Diagnosis Event/Progress production.
- Implement the Phase 1B transport migration as DPOMAgent HTTP Outbox to Kafka Outbox, with one canonical event, durable state-before-publication, HTTP/Kafka parity, replay, rollback and observable cutover.
- Keep DPOMBaseMCPServer stateless with respect to diagnosis and limited to bounded evidence collection, discovery, deterministic packaging/correlation and configurable OBS artifact transfer.
- Keep HuaweiCloudAlarmChangeGuard as the only service in this scope allowed to change APM/CES/AOM alarm state; no mutation tool returns to DPOMBase.
- Keep SRE Intelligence Service authoritative for evaluation facts and evaluated-report projections; make diagnosis-only report input originate from immutable DPOMAgent facts.
- Move producer-owned shared contracts and cross-phase ADR/acceptance sources into version-controlled DPOMAgent paths; remove Maven/test dependence on unversioned sibling machine-specific workspace contracts or root governance files.
- Re-run Phase 1 and Phase 5 clean-clone, real MySQL/Kafka and cross-service acceptance against the corrected ownership, then audit Phase 2–4 prerequisite claims without relabeling a blocked gate as success.

## Capabilities

### New Capabilities

- `dpomagent-investigation-runtime`: Durable, restartable and auditable Investigation authority in DPOMAgent, including bounded progress and diagnosis-only source projections.
- `diagnosis-kafka-delivery`: Transactional DPOMAgent Diagnosis Event/Progress outbox delivery to Kafka with compatibility HTTP parity, replay, cutover and rollback.
- `diagnostic-report-contract`: Versioned canonical diagnosis-only and evaluated diagnostic report contracts, immutable revisions and deterministic projections under corrected service ownership.

### Modified Capabilities

- `ai-sre-service-boundaries`: Strengthen the evidence-only DPOMBase boundary, DPOMAgent diagnosis ownership, SRE evaluation/report ownership and AlarmChangeGuard mutation boundary.
- `diagnosis-event-contract`: Require DPOMAgent as producer authority and remove sibling-repository build coupling while preserving transport-independent canonical semantics.

## Impact

- `DPOMAgent`: authoritative domain/persistence, outbox/Kafka publisher, progress API, diagnosis-only report builder, producer contracts and portable platform ADR/acceptance documentation.
- `DPOMBaseMCPServer`: boundary and consumer compatibility verification only; no diagnosis state, model, report or Kafka producer is reintroduced.
- `SREIntelligenceService`: self-contained contract consumption, HTTP/Kafka parity ingestion and evaluated-report source adapter updates.
- `DeepEvalService`: no ownership change; regression verification only.
- `HuaweiCloudAlarmChangeGuard`: mutation-boundary verification and pending repository cleanup without moving mutation into DPOMBase.
- Local Kafka 9092, MySQL 3306 and controlled non-production fixtures are used for acceptance. Credentials and evidence bodies remain runtime-only and are never committed.
