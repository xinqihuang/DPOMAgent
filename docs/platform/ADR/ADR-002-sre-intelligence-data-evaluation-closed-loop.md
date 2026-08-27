# ADR-002: SRE Intelligence Data & Evaluation Closed Loop

- Status: Accepted; authority boundary amended 2026-08-27
- Date: 2026-08-25
- Scope: Phase 1–4 数据与评测闭环详细决策
- Governing architecture: `../ADR.md`

## Context

The repository already proves the Phase 1A compatibility vertical slice: a versioned diagnosis event is ingested idempotently by SRE Intelligence Service, a deterministic Java rule and two semantic judges are executed, an authoritative report is persisted, and the run can be replayed from persisted facts. The acceptance evidence is recorded in `SREIntelligenceService/docs/evaluation-suite-acceptance-report.md`. This proves the evaluation path, not the final service topology.

The compatibility implementation spans DPOMAgent, SRE Intelligence Service, and DeepEval Service. The corrected
Phase 1 target keeps the online Diagnosis and Investigation Runtime in DPOMAgent, migrates only its HTTP Outbox
delivery toward Kafka, and keeps DPOMBaseMCPServer as a bounded evidence-tool service. Data engineering and
evaluation control-plane responsibilities remain in SRE Intelligence Service. Phase 1 remains in progress until
the corrected acceptance gates are published.

The platform must turn production diagnosis history into governed Incident Cases, versioned evaluation datasets, replay results, failure attribution, and release decisions. It must preserve evidence lineage and fail closed whenever required evidence or judge output is missing.

## Decision

The target architecture contains four core backend deployment units. HuaweiCloudAlarmChangeGuard is an additional
independent safety boundary for approved APM/CES/AOM mutations.

### DPOMAgent

DPOMAgent is the online diagnosis system of record and orchestration authority. It owns:

- Diagnosis Runtime and Investigation Runtime;
- Incident, Investigation, Observation, Hypothesis, Conclusion, ToolUse and execution provenance;
- LLM orchestration and bounded calls to evidence tools;
- transactional HTTP/Kafka outbox publication of versioned `diagnosis-event` and progress records;
- authenticated bounded diagnosis-source and SSE progress APIs.

It does not own evaluation datasets, Judge execution, aggregate evaluation, Release Gate decisions, provider SDK
implementations or direct APM/CES/AOM mutation.

### DPOMBaseMCPServer

DPOMBaseMCPServer is a stateless production-facing evidence gateway. It owns:

- Huawei Cloud MCP tools and provider SDK isolation;
- CMDB and topology access;
- `colbymchenry/codegraph` integration for code evidence;
- controlled Huawei Cloud OBS evidence artifacts;

It does not own models, diagnosis or Investigation state, ToolUse policy, business orchestration, reports, Kafka
publication, alarm mutation, evaluation datasets, judge aggregation, failure classification or release decisions.

### SRE Intelligence Service

SRE Intelligence Service is the data and evaluation control plane. It owns:

- ODS, DWD, DWS, and ADS logical data layers;
- Spring Batch jobs for bounded curation, dataset construction, and replay orchestration;
- immutable Incident Case versions and evidence lineage;
- Bronze, Silver, and Gold review lifecycle;
- versioned evaluation datasets and lifecycle governance;
- Agent replay evaluation;
- deterministic Java Rule Judge;
- semantic judge orchestration and persisted Judge Results;
- score aggregation and human-agreement measurements;
- failure classification and attribution;
- capability-gap and improvement recommendations;
- fail-closed Release Gate decisions.

It must not query another service's database, hold Huawei Cloud production credentials, execute general production writes, or execute LLM-as-a-Judge logic in-process.

### DeepEval Service

DeepEval Service is a stateless Python evaluation engine accessed through a bounded, versioned HTTP contract. It owns:

- LLM-as-a-Judge execution;
- RootCauseJudge;
- FaultSourceJudge;
- FaultChainJudge;
- EvidenceGroundingJudge;
- TaskCompletionJudge;
- InvestigationQualityJudge.

It does not own datasets, scheduling, persistence, aggregation, release gates, production credentials, or diagnosis lifecycle state.

## Logical data model

The ODS/DWD/DWS/ADS names describe ownership and transformation semantics; they do not imply a distributed data-lake platform.

| Layer | Purpose | Representative records |
|---|---|---|
| ODS | Preserve immutable source facts and ingestion receipts | Diagnosis Event, source version, correlation IDs, OBS artifact reference, content digest |
| DWD | Normalize incident facts and reconstruct evidence relationships | Incident, topology snapshot, evidence item, fault source, fault chain |
| DWS | Curate reusable evaluation cases | Incident Case Version, review record, Bronze/Silver/Gold status, ground truth |
| ADS | Serve evaluation and improvement decisions | Dataset Version, Replay Run, Judge Result, failure attribution, capability gap, Release Gate |

Evidence bodies remain bounded and may be stored in OBS; MySQL stores authoritative metadata, lineage, state, and bounded evaluation projections.

## Runtime flow

```text
Portal
  |
  | REST / SSE
  v
DPOMAgent ----------------------> DPOMBaseMCPServer
  | bounded MCP evidence tools     | Huawei Cloud / CMDB / codegraph / OBS
  | Kafka: versioned diagnosis-event
  v
SRE Intelligence Service
  |  ODS -> DWD -> DWS -> ADS
  |  Spring Batch / Replay / Java Rule Judge / Release Gate
  |
  | bounded versioned HTTP
  v
DeepEval Service
  |  six stateless semantic judges
  v
Judge Result -> SRE Intelligence persistence and aggregation
```

The consumer acknowledges a diagnosis event only after durable receipt. Duplicate events with the same identity and digest are idempotent; the same identity with a different digest is quarantined and cannot silently replace prior facts. Every derived record retains incident, investigation, run, source-event, artifact, schema, component, prompt, model, rule, and dataset versions as applicable.

## Phase model

| Phase | Status | Outcome |
|---|---|---|
| Phase 1A | Complete | Compatibility HTTP vertical slice: durable ingestion, Eval Case, deterministic rule, two semantic judges, aggregate report, and persisted replay |
| Phase 1B | In Progress | DPOMAgent-owned authority, Kafka/SSE path, HTTP compatibility and transport parity |
| Phase 2 | Complete | Case/Dataset/replay/agreement plus fake and approved-model fixed six-Judge gates accepted |
| Phase 3 | Complete | Failure attribution, capability-gap analysis, improvement recommendation, and Release Gate accepted |
| Phase 4 | Complete | Human-governed inactive improvement proposals, replay proof and bounded handoff accepted |

Phase 1A/1B are milestones within one Phase 1, not separate long-term phases. Phase documents under `docs/phases` are the authoritative task panorama. OpenSpec change artifacts define implementation-ready requirements but do not mean that unchecked code exists.

## Technology decisions

- Java 21, Spring Boot 3.x, and Maven for DPOMAgent, DPOMBaseMCPServer, SRE Intelligence Service and HuaweiCloudAlarmChangeGuard;
- MySQL for authoritative state and bounded analytical projections;
- Kafka for diagnosis-event delivery and progress integration;
- Spring Batch for restartable, bounded offline processing;
- SSE for live progress delivery;
- Micrometer and Actuator for readiness, capacity, and low-cardinality telemetry;
- Huawei Cloud OBS for controlled large evidence artifacts;
- Python, FastAPI, and DeepEval for the stateless semantic judge service;
- `colbymchenry/codegraph` for code context.

The platform will not introduce Flyway, Flink, Spark, Iceberg, or MLflow. Database changes must use reviewed, versioned SQL release artifacts executed by the deployment process, with forward/rollback procedures and compatibility checks.

## Safety and governance

- Service communication uses versioned APIs, events, or immutable artifacts; cross-service database access is forbidden.
- Raw credentials, prompts, model output, and evidence bodies are excluded from logs and metric labels.
- Unknown schema versions, missing required evidence, timeouts, judge errors, insufficient samples, and incompatible versions fail closed.
- Dataset publication, Gold promotion, gate waiver, and improvement adoption require authenticated human decisions and append-only audit.
- The platform may recommend improvements but may not perform autonomous production remediation.

## Migration and compatibility

The Phase 1A vertical slice remains the accepted compatibility baseline while Phase 1B changes transport.
Investigation Runtime and diagnosis-event authority stay in DPOMAgent. Moving its HTTP Outbox delivery toward Kafka
requires contract compatibility, durable-state-before-publication, dual-path parity, replay verification and rollback.
No existing source record may be re-parented to DPOMBaseMCPServer.

Existing Phase 1A HTTP ingestion can remain as a compatibility adapter until the Kafka path has demonstrated equivalent idempotency, ordering, quarantine, replay, and observability. This ADR does not authorize an immediate destructive migration.

## Consequences

The four-core-service model separates diagnosis authority, evidence collection, evaluation control and Python-specific
Judge execution. SRE Intelligence therefore requires strict modular boundaries between data ingestion, curation,
evaluation, and improvement. MySQL-based layered data is operationally simpler for current scale but requires bounded
batches, explicit retention, and capacity monitoring. Human review is deliberately part of the data contract, not an
informal external process.
