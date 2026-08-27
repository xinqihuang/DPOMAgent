# Cross-Service Surface and Contradiction Inventory

- Captured: 2026-08-27
- Scope: active production API/tool/contract surfaces plus Phase 1/5 ownership claims

## Active runtime surfaces

### DPOMAgent

- 23 Spring mapping annotations.
- Investigation authority endpoints currently include create, exact investigation, steps, evidence and conclusion under `/api/v1/investigations`.
- Existing publication surface includes the authenticated diagnosis replay endpoint and an HTTP outbox delivery adapter.
- Handoff/script/change-guard proxy endpoints are separate from diagnosis event publication.
- No Kafka publisher, bounded authoritative progress SSE or Phase 5 canonical report API exists at this baseline.

### DPOMBaseMCPServer

The explicit evidence-only allowlist contains 31 MCP tools:

```text
batch_query_ces_metric_data
correlate_incident
discover_resource_context
get_evidence_package
get_service_topology
head_evidence_package
list_alarm_notify
list_alarms
list_aom_events
list_aom_metrics
list_apm_alarm_data
list_apm_business
list_ces_metrics
list_lts_log_groups
list_lts_log_streams
list_notification_masks
put_evidence_package
query_aom_metric_data
query_ces_metric_data
query_logs
query_lts_log_context
query_lts_logs
query_traces
resolve_resource_candidates
search_apm_application
show_apm_monitor_item_view_config
show_apm_trend
show_clob_detail
show_env_monitor_items
show_event_detail
show_trace_events
```

There is no active Investigation, report, Diagnosis Event producer or alarm mutation endpoint in the current DPOMBase reactor.

### SREIntelligenceService

- 130 Spring route/mapping annotations across ingestion, Phase 1 evaluation, Phase 2 cases/datasets/replay/agreement, Phase 3 governance, Phase 4 improvement and Phase 5 reports.
- One active `DiagnosisEventKafkaListener` for topic configuration defaulting to `dpom.diagnosis-event.v2`.
- The Phase 1 HTTP compatibility input remains `/api/v1/diagnosis-events`.
- The current Phase 5 report API is `/api/v5/diagnostic-reports` with canonical lookup/history/replay, Markdown, HTML, PDF, evidence and readiness routes.

### DeepEvalService

Exactly four FastAPI routes are active: `/health/live`, `/health/ready`, `/metrics` and `POST /v1/evaluations`. It has no diagnosis lifecycle or report store.

### HuaweiCloudAlarmChangeGuard

- 15 Spring route/mapping annotations.
- Query routes expose capabilities/rules/alarms.
- Guarded mutation lifecycle routes create an operation, approve, shield, restore/retry and query audit/state.
- The current dirty worktree adds APM rule query/update transport and must remain isolated from DPOMBase.

## Active contract assets

The unversioned workspace contract root currently contains:

- Diagnosis Event v1/v2;
- Diagnosis Progress v1;
- Evidence Manifest v1;
- Diagnostic Report v1 including canonical vectors and alarm `16557989` fixtures;
- DeepEval semantic Judge, evaluation suite and Phase 2–4 contracts.

DPOMAgent has partial copied contract test resources and producer models, while SRE contains v1 fixtures but still injects v2 conformance/test sources from sibling `../contracts`. This is not portable and is assigned to tasks 3.1–3.3.

## Contradicted ownership claims

The following current or historical sources still assign Investigation, Kafka, progress or diagnosis-only report authority to DPOMBase and therefore cannot support a current PASS decision:

- `docs/phases/PHASE-1.md` lines 31 and 48;
- `docs/phases/PHASE-5.md` line 42;
- `docs/ADR/ADR-002-sre-intelligence-data-evaluation-closed-loop.md` Phase 1B row;
- `docs/phase1b/phase1b-acceptance-report.md` ownership decision;
- `docs/phase1b/phase1b-requirement-evidence-matrix.md` DPOMBase persistence/publication evidence;
- `docs/phase1b/dpombase-kafka-publication-report.md`;
- `docs/phase1b/dpombase-progress-report.md`;
- `docs/phase1b/phase1a-characterization-baseline.md` target migration statement;
- `docs/phase5/requirement-evidence-matrix.md` diagnosis-only authority statement;
- `docs/openspec/changes/add-sre-intelligence-evaluation-closed-loop/{proposal,design,tasks}.md`;
- `openspec/changes/complete-phase1-three-service-convergence/{proposal,design,tasks}.md` and all DPOMBase authority delta specs;
- `openspec/changes/add-phase5-diagnostic-report-standardization/{design,tasks}.md` and its service-boundary delta spec.

Those completed OpenSpec directories are retained as historical decision records. Current main `openspec/specs/ai-sre-service-boundaries/spec.md` already states the corrected DPOMAgent authority, and the new realignment change supplies the replacement observable requirements.

## Reconciliation rule

No stale Phase 1/5 source is deleted merely to make a scan pass. Portable roadmap/status and acceptance sources will explicitly mark the prior decision historical, point to the replacement change and return to In Progress until the corrected runtime passes its own gates.
