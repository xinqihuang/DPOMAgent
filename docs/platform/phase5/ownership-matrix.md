# Phase 5 Ownership Matrix

| Component | Authoritative responsibilities | Non-authoritative surfaces | Rollback boundary |
|---|---|---|---|
| DPOMAgent | Incident, Investigation, Run, observations, hypotheses, conclusions, diagnosis evidence references and immutable diagnosis-only source/report revisions | Evaluated outcome and disposable rendered layout | Disable diagnosis/report publication; retain immutable Investigation and report history |
| DPOMBaseMCPServer | Bounded evidence collection and immutable evidence references | Diagnosis inference, Investigation state, report fields, rendering, Kafka and cloud mutation | Disable affected evidence tools; diagnosis/report authority remains in DPOMAgent |
| SRE Intelligence Service | Eval Case/Run/Suite/Judge facts and immutable evaluated final-report projection | Diagnosis inference, evidence bodies and rendered layout | Disable evaluated generation/rendering; retain reports and all Phase 1–4 authority |
| DeepEval Service | One versioned individual Judge execution result | Aggregate status, final report and persistence | Disable calls; missing Judge forces `INCOMPLETE` |
| Portal | Authenticated display, controlled evidence dereference and export from approved projection | Fact synthesis, confidence strengthening, gap hiding and alternate report storage | Disable renderer/export; canonical JSON remains readable |

No component reads another service's database. Cross-service inputs are bounded versioned API/event/artifact contracts. HTML, PDF, Markdown and Portal objects are disposable projections and never become a source for replay.
