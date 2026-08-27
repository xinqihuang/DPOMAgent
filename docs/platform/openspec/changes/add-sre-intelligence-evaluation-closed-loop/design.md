# Design: SRE Intelligence Data & Evaluation Closed Loop

## Context

The Phase 1A compatibility implementation already contains framework-neutral SRE core domains for ingestion, rule judging, semantic runs, and evaluation suites; MyBatis persistence adapters; bounded HTTP APIs; Micrometer/Actuator visibility; and a stateless DeepEval service with a fixed two-judge catalog. The accepted test report proves idempotency, restart behavior, real MySQL compatibility, persisted replay, and fail-closed aggregation.

Phase 1B changes ownership: DPOMBaseMCPServer becomes the authoritative online Diagnosis and Investigation Runtime and publishes Kafka diagnosis events; SRE Intelligence becomes both the layered data service and evaluation control plane; DeepEval remains stateless. Because current code does not yet match that ownership, migration must coexist with Phase 1A contracts.

## Goals

- Preserve immutable diagnosis and evidence lineage from source event to release decision.
- Build governed Incident Case and Dataset Version lifecycles.
- Make replay and judging reproducible, bounded, restartable, and auditable.
- Measure semantic-judge agreement with humans before using a judge in a release policy.
- Explain evaluation failures through evidence-backed attribution and capability gaps.
- Fail closed when producing Release Gate decisions.
- Keep service dependencies acyclic and production execution out of scope.

## Non-goals

- General-purpose data lake or streaming analytics platform.
- Autonomous production remediation.
- Automatic adoption of Improvement Agent proposals.
- Cross-service database joins.
- Application-managed database migration frameworks or replacement of the bounded MySQL/OBS design with Flink, Spark, Iceberg, or MLflow.

## Service and module boundaries

### DPOMBaseMCPServer

Suggested internal modules are contracts, cloud evidence adapters, CMDB/topology, codegraph adapter, Investigation domain, Diagnosis orchestration, Kafka publication, and SSE presentation. Provider SDK DTOs and credentials terminate here. The durable domain model is authoritative for online diagnosis; published events are immutable integration facts, not remote commands.

### SRE Intelligence Service

Retain a framework-neutral core and adapters. Split the core by bounded context:

- ingestion and ODS receipts;
- evidence catalog and DWD normalization;
- fault reconstruction;
- Incident Case curation and Gold review;
- Dataset lifecycle;
- replay/evaluation orchestration;
- failure attribution and improvement analysis;
- gate policy and decisions.

Spring Batch, Kafka consumers, HTTP controllers, MyBatis, MySQL SQL, OBS clients, Micrometer, and Actuator remain adapters around core ports. Database changes use versioned reviewed SQL release artifacts managed by deployment, not an application migration framework.

### DeepEval Service

Keep FastAPI, fixed catalog, bounded request/response models, provider adapter, fake-model acceptance adapter, and no persistence. Every Judge Definition binds ID, semantic version, rubric/criteria digest, model alias, threshold, and output schema version.

## Data architecture

### ODS

ODS stores immutable Diagnosis Event receipts and bounded source payload projections. The unique source key and canonical digest enforce idempotency. Source ordering is scoped to an investigation/run partition. Unknown versions and conflicts enter quarantine with stable reason codes.

### DWD

DWD normalizes incident identity, evidence metadata, topology snapshot, code reference, fault-source candidate, fault-chain edge, and provenance. Transformations bind their algorithm and schema versions. Reprocessing creates a new derived version instead of overwriting prior facts.

### DWS

DWS stores immutable Incident Case Versions. Bronze cases are machine-curated; Silver cases have passed structural and evidence validation; Gold cases carry authenticated human-approved ground truth. Review state and case content are separate append-only histories.

### ADS

ADS stores Dataset Versions, replay/evaluation runs, per-judge results, agreement snapshots, aggregates, failure attribution, capability gaps, recommendations, gate decisions, and waivers. Reports reference authoritative records rather than duplicating evidence bodies.

## Key identities and versioning

All public records use stable opaque IDs and explicit versions. At minimum, lineage can include:

`incidentId -> investigationId -> diagnosisRunId -> diagnosisEventId -> evidence/artifact IDs -> incidentCaseId@version -> datasetId@version -> replayRunId -> judgeResultId -> attributionId -> gateDecisionId`.

Digests use canonical content and identify frozen inputs. Mutable labels and display metadata never participate in identity. Optimistic versions protect reviewer and operator commands from stale writes.

## Ingestion and delivery

DPOMBaseMCPServer publishes versioned `diagnosis-event` records to Kafka after authoritative diagnosis state is durable. SRE consumes with at-least-once semantics, validates size/schema/provenance, writes the ODS receipt and audit atomically, then acknowledges. Duplicate identity plus equal digest is success-without-duplication; equal identity plus different digest is quarantined.

The Phase 1A authenticated HTTP endpoint remains a compatibility adapter. During Phase 1B, both transports feed the same ingestion application port and idempotency rules. Cutover requires contract parity, replay parity, lag/capacity evidence, rollback, and source-authority approval.

## Batch processing

Spring Batch jobs are bounded by time, item count, concurrency, and retry budgets. Job parameters include input watermark/snapshot, transformation version, and output target version. Steps are restartable and item processing is idempotent. A stopped or failed job never publishes a partial Dataset Version or Gold state.

## Review and dataset lifecycle

Case content is immutable. Reviews append decisions against an exact case version and review-policy version. Gold promotion requires the configured reviewer policy and validation checks. Dataset membership is a list of exact case versions frozen at approval. Activation is an explicit audited command; deprecation stops new selection but preserves replayability.

## Replay and judging

A Replay Plan freezes Dataset Version, candidate component versions, evaluation suite, rules, judges, model aliases, prompts/rubrics, thresholds, budgets, and report schema. Preparation persists all case work before execution. Workers claim bounded leases and persist individual outcomes.

Java Rule Judge stays deterministic and local to SRE core. SRE invokes DeepEval over versioned HTTP for each required semantic judge. The six semantic results remain independent. Invalid output, timeout, budget exhaustion, missing evidence, or unavailable service yields an explicit non-passing result. Aggregation never converts uncertainty to PASS.

## Judge-human agreement

Human labels bind the same case version, rubric version, and outcome scale as the judge. Agreement snapshots freeze judge version, sample membership, human labels, calculation method, and minimum-sample policy. Reports include sample count, raw agreement, confusion matrix, and a configured chance-corrected metric. Release-critical eligibility is a policy decision owned by SRE, not DeepEval.

## Failure attribution and improvement analysis

Attribution consumes immutable replay results and uses a versioned taxonomy. It records observed facts separately from inferred cause and confidence. Capability gaps aggregate only comparable records and retain supporting and contradicting cases. Recommendations are advisory records containing target surface, expected benefit, risk, validation dataset, and rollback condition.

## Release Gate

A Gate Policy binds the approved baseline, compatible Dataset Version or cohort rule, required suite/judges, agreement eligibility, freshness, minimum samples, regression thresholds, and decision schema. ALLOW is possible only when all requirements are complete and pass. All other states BLOCK with stable reasons. Decisions are immutable. Waivers are separate, authenticated, time-bounded, and audited.

## Observability and security

- Actuator readiness exposes dependency availability and capacity without secrets.
- Micrometer labels are bounded to component, operation, state, outcome, and stable error class.
- SSE payloads expose progress summaries, not evidence bodies or raw model output.
- Logs and audit records exclude credentials, prompts, evidence bodies, and arbitrary exception payloads.
- OBS artifacts use bounded size/type, checksum, allow-listed location, retention, and access control.
- DeepEval and SRE never receive Huawei Cloud AK/SK.

## Rollout

1. Preserve and characterize Phase 1A behavior.
2. Complete Phase 1B contracts, runtime ownership migration, compatibility adapters, and Kafka equivalence before changing source authority.
3. Deliver Phase 2 data governance behind default-off controls and validate on non-production cases.
4. Deliver Phase 3 attribution and gate in report-only mode, then enforce only after agreement and false-decision review.
5. Consider Phase 4 only after Phase 3 operational acceptance.

Rollback disables new consumers, jobs, review mutations, replay dispatch, or gate enforcement independently while retaining immutable facts. No rollback deletes evidence or rewrites decisions.

## Open questions to resolve during implementation planning

- Exact Kafka topic names, partition key, retention, and schema registry convention.
- Reviewer quorum and separation-of-duties policy for Gold and waivers.
- Dataset tier quotas and retention periods.
- Approved agreement statistic and thresholds per judge.
- Initial failure taxonomy and gate policy catalog.
- Ownership and cutover plan for existing DPOMAgent Investigation data.
