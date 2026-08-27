# Change: Add SRE Intelligence Evaluation Closed Loop

## Why

Phase 1A proves that one diagnosis can be ingested, evaluated, reported, and replayed safely through the compatibility DPOMAgent HTTP path. Phase 1B must still converge runtime ownership into DPOMBaseMCPServer and prove Kafka/HTTP parity. Neither milestone provides governed Incident Case history, human-approved ground truth, immutable Dataset Versions, calibrated multi-judge evaluation, failure attribution, or release governance.

This change establishes the implementation context for completing Phase 1B and then building the SRE Intelligence Data & Evaluation closed loop in Phase 2–4. The accepted topology contains three backend deployment units.

## What Changes

- Consolidate the target online Diagnosis and Investigation Runtime, Huawei Cloud MCP tools, CMDB, codegraph, Kafka diagnosis-event publication, and SSE progress in DPOMBaseMCPServer.
- Evolve SRE Intelligence Service into the owner of ODS/DWD/DWS/ADS logical layers, Spring Batch processing, Incident Case Versions, Bronze/Silver/Gold review, Dataset Versions, replay evaluation, Java Rule Judge, result aggregation, failure attribution, capability gaps, recommendations, and Release Gate decisions.
- Expand DeepEval Service into a fixed, stateless six-judge catalog: RootCause, FaultSource, FaultChain, EvidenceGrounding, TaskCompletion, and InvestigationQuality.
- Add explicit provenance, immutable versioning, human review, judge-human agreement, fail-closed behavior, retention, redaction, observability, and rollback requirements.
- Preserve the Phase 1A compatibility slice while completing Phase 1B Kafka ingestion and ownership cutover; do not destructively replace the accepted HTTP path until equivalence is proven.

## Capabilities

### New capabilities

- `incident-ingestion`: durable, idempotent, version-aware diagnosis-event intake into ODS.
- `incident-evidence`: bounded evidence metadata, artifact integrity, topology/code lineage, and redaction controls.
- `fault-reconstruction`: deterministic, versioned reconstruction of fault source and fault chain facts.
- `incident-case-curation`: immutable Incident Case Versions and Bronze/Silver/Gold human review.
- `evaluation-datasets`: immutable Dataset Versions, lifecycle governance, selection lineage, and retention.
- `agent-replay-evaluation`: restartable dataset replay, Java rules, six semantic judges, agreement measurement, and authoritative reports.
- `failure-attribution-release-gate`: failure taxonomy, capability gaps, recommendations, and fail-closed release decisions.

### Modified capabilities

- The accepted Phase 1A diagnosis-event, Eval Case, semantic-judge, and evaluation-suite contracts remain compatibility foundations and must be evolved through explicit versions rather than silent mutation.

## Impact

- Primary repositories: `DPOMBaseMCPServer`, `SREIntelligenceService`, and `DeepEvalService`.
- Shared contracts: diagnosis event, evidence manifest, Incident Case, Dataset Version, Replay Run, Judge Result, attribution, recommendation, and gate decision.
- Infrastructure: MySQL, Kafka, Huawei Cloud OBS, and service-to-service HTTP.
- Operations: Spring Batch job control, SSE progress, Micrometer/Actuator readiness and capacity, append-only audit, retention, and replay evidence.
- Migration: DPOMAgent Phase 1A publication remains supported until Phase 1B proves DPOMBaseMCPServer authoritative runtime ownership and Kafka delivery equivalence.

## Constraints

- Planning only; this change does not implement business code.
- Java 21, Spring Boot 3.x, Maven, MySQL, Kafka, Spring Batch, SSE, Micrometer/Actuator, Huawei Cloud OBS, DeepEval, and `colbymchenry/codegraph` are the approved stack.
- Do not introduce Flyway, Flink, Spark, Iceberg, or MLflow.
- Do not add cross-service database access, production credentials to SRE/DeepEval, or autonomous production remediation.
