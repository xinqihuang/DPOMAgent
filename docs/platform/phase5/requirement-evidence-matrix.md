# Phase 5 Requirement-to-Evidence Matrix

Acceptance baseline: contract `diagnostic-report/1.0.0`, template `diagnostic-report-standard@1.0.0`, DPOMBaseMCPServer `0.0.1-SNAPSHOT`, SRE Intelligence Service `0.1.0-SNAPSHOT`.

| Task | Objective repository evidence |
|---|---|
| 1.1 | `docs/phases/PHASE-5.md`; root `ADR.md` Phase 5 reference |
| 1.2 | `docs/phase5/report-inventory.md` |
| 1.3 | `docs/phase5/ownership-matrix.md` |
| 1.4 | Contract `README.md`; `DiagnosticReportContract.java` |
| 2.1 | `contracts/diagnostic-report/v1/diagnostic-report.schema.json` |
| 2.2 | `canonical-vectors.json`; Java and Python canonical validators |
| 2.3 | `DiagnosticReportValidator.java`; contract tests |
| 2.4 | Five `fixtures/valid` profile/outcome fixtures |
| 2.5 | `fixtures/invalid/cases.json` with eight negative cases |
| 2.6 | `scripts/validate-diagnostic-report.py`; Java conformance tests |
| 2.7 | Contract README compatibility, gap and extension catalogs |
| 3.1 | DPOMBase report domain values, builder and ports |
| 3.2 | `DiagnosisOnlyReportBuilder` and its tests |
| 3.3 | DPOMBase builder validation and incomplete-evidence tests |
| 3.4 | DPOMBase MyBatis repository/mapper and deployment `003_diagnostic_report_*` SQL |
| 3.5 | DPOMBase service, authorization, controller and default-off properties |
| 3.6 | DPOMBase builder, authority, persistence and application-service tests |
| 4.1 | `Phase5EvaluationAuthorityAdapter`; architecture boundary tests |
| 4.2 | `EvaluatedDiagnosticReportBuilder`; persisted Phase 2 authority adapter |
| 4.3 | Validator, builder and contract tests |
| 4.4 | SRE Phase5 persistence, mapper XML and deployment SQL |
| 4.5 | SRE service, controller, authentication and API tests |
| 4.6 | Builder/service tests and real `Phase2EndToEndMySqlContractIT` |
| 5.1 | `DiagnosticReportViewModel.java` |
| 5.2 | `templates/diagnostic-report-standard@1.0.0.md` |
| 5.3 | `DiagnosticReportRenderer.java`; renderer snapshots |
| 5.4 | Portal projection and semantic-parity tests |
| 5.5 | HTML/PDF adapters through the normalized view and parity tests |
| 5.6 | Seven SHA-256 snapshots in `Phase5DiagnosticReportContractTest` |
| 6.1 | Pre-persistence/pre-render validation and prohibited-content fixtures |
| 6.2 | `Phase5ReportSurfaceSecurityTest`; bounded audit models |
| 6.3 | `Phase5ReportAuthentication` and controller/authentication tests |
| 6.4 | Low-cardinality report metrics and readiness/capacity properties |
| 6.5 | `docs/phase5/operations-runbook.md` |
| 7.1 | `docs/phase5/legacy-report-policy.md` |
| 7.2 | Golden alarm `16557989` alert/recovered fixtures |
| 7.3 | Real Kafka/MySQL cross-service acceptance with seven Judge results |
| 7.4 | Replay, revision, renderer, version, redaction, bounds and default-off tests |
| 7.5 | This matrix and `docs/phase5/acceptance-report.md` |
| 7.6 | Phase 5 exit criteria and strict OpenSpec validation |

The schema suite covers bounded structure, independent outcome axes, evidence linkage and unavailable-Judge failure closure. DPOMBase covers authoritative diagnosis-only projection and immutable revisions. SRE covers evaluated projection, every persisted individual Judge result, conflict quarantine, deterministic replay and renderer parity. The golden alarm revisions encode the Eden/CodeCache distinction and limitations. Real local acceptance crosses Kafka and MySQL without production evidence or credentials in artifacts.
