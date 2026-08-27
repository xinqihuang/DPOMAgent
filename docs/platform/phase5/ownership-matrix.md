# Phase 5 Ownership Matrix

| Component | Authoritative responsibilities | Non-authoritative surfaces | Rollback boundary |
|---|---|---|---|
| DPOMBaseMCPServer | Incident, Investigation, Run, observations, hypotheses, conclusions, diagnosis evidence references and diagnosis-only source projection | Markdown prose, evaluation status and Portal layout | Disable diagnosis-report generation; retain immutable reports and existing Investigation authority |
| SRE Intelligence Service | Eval Case/Run/Suite/Judge facts and immutable evaluated final-report projection | Diagnosis inference, evidence bodies and rendered layout | Disable evaluated generation/rendering; retain reports and all Phase 1–4 authority |
| DeepEval Service | One versioned individual Judge execution result | Aggregate status, final report and persistence | Disable calls; missing Judge forces `INCOMPLETE` |
| AgentArts Workflow | Idempotent scheduling, notification and approval trigger | Report facts, status mutation and system-of-record storage | Stop retries; authoritative service returns existing frozen report |
| Portal | Authenticated display, controlled evidence dereference and export from approved projection | Fact synthesis, confidence strengthening, gap hiding and alternate report storage | Disable renderer/export; canonical JSON remains readable |

No component reads another service's database. Cross-service inputs are bounded versioned API/event/artifact contracts. HTML, PDF, Markdown and Portal objects are disposable projections and never become a source for replay.
