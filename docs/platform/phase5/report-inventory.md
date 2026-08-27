# Phase 5 Existing Report Inventory

Hand-authored diagnostics and historical acceptance reports are legacy presentation artifacts. They remain readable but are not authoritative unless every retained field can be resolved to a frozen source contract without fabrication.

| Retained report field | Authoritative source | Canonical Phase 5 field |
|---|---|---|
| Alarm/event identity and lifecycle | DPOMAgent persisted Incident and versioned provider evidence reference | `identity.incidentId`, `timeline`, `evidenceReferences` |
| Investigation and diagnosis run | DPOMAgent Investigation Runtime | `identity.investigationId`, `identity.runId` |
| Target service/instance/IP | Persisted diagnosis target projection and evidence scope | `target` |
| Incident and observation windows | Incident fact and evidence collection metadata | `incidentWindow`, `evidenceReferences[].window` |
| Metric observations | DPOMAgent bounded Observation plus immutable DPOMBase evidence reference | `observations`, `evidenceReferences` |
| Hypotheses and alternatives | DPOMAgent persisted Hypothesis | `hypotheses` |
| Root-cause conclusion and confidence | DPOMAgent persisted Conclusion; confidence is bounded and never strengthened by a renderer | `conclusions` |
| Evidence gaps/query limitations | Persisted missing-capability facts and validation results | `gapCodes` |
| Recommendations | Persisted advisory diagnosis output; never an execution record | `recommendations` |
| Eval Case, Run and Suite | SRE Intelligence persisted Phase 2 authority | `evaluation.lineage` |
| Individual Judge result/status/version | SRE Intelligence persisted JudgeResult | `evaluation.judges` |
| Evaluation aggregate | SRE fail-closed aggregation | `evaluation.outcome` |
| Component/model/prompt/rule/schema versions | Producing service contract metadata | `provenance` |
| Revision and recovery update | Owning report store, append-only | `revision`, `supersedesReportId`, `changeReasons` |
| Markdown headings, tables and prose | Non-authoritative versioned renderer | normalized view model only |

Known legacy examples include `DPOMBaseMCPServer/docs/diagnostics/*.md` and SRE acceptance reports. Their prose, numeric confidence wording and section order are not ingested as facts. The alarm `16557989` report can be migrated because its event, memory-pool observations and lifecycle revision are represented by a dedicated bounded golden fixture.
