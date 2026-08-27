# Phase 2 Requirement-to-Evidence Matrix

This matrix maps every Phase 2 exit criterion to executable, repository-owned evidence. Runtime credentials and evidence bodies are intentionally absent.

| Exit criterion | Primary implementation evidence | Automated verification | Result |
|---|---|---|---|
| Two immutable Case Versions with lineage | Incident Case domain, MyBatis mappings, append-only schema | `Phase2PersistenceTest`, `Phase2PersistenceMySqlContractIT`, case API tests | PASS |
| Authenticated Bronze-Silver-Gold review and stale-write safety | curation/review services, exact version and optimistic state rows | curation core/web tests, MySQL optimistic-lock contract | PASS |
| Fixed six semantic Judges execute through DeepEval | fixed catalogs and strict SRE/DeepEval HTTP adapters | DeepEval pytest/Ruff/mypy, `Phase2SemanticCrossServiceTest`, gated real-model acceptance | PASS |
| Frozen Judge-human agreement is reproducible | kappa calculator, frozen sample/confusion/snapshot rows | `CohenKappaCalculatorTest`, agreement persistence/API tests | PASS |
| Approved Dataset Version has immutable membership/digest | Dataset lifecycle/materialization services and schema | Dataset domain/API/batch tests, H2 and MySQL contracts | PASS |
| Dataset replay is restartable with authoritative per-case results | Replay Plan/work/lease/attempt/result/report services | replay core/web tests and `Phase2EndToEndMySqlContractIT` | PASS |
| Retention, redaction, capacity, recovery, and rollback are documented/tested | Phase 2 runbook and safe SQL artifacts | boundary/redaction tests, restart/capacity tests, strict verification | PASS |

## Gate evidence

| Gate | Reproduction target | Expected evidence |
|---|---|---|
| SRE offline | `mvn -q -o clean verify` | zero failures/errors; external tests explicitly gated |
| Real MySQL/Kafka | Maven `mysql-contract` profile with explicit mutation consent | all contract ITs pass; Phase 2 E2E prints 7 Judges and recovered restart |
| DeepEval offline | `DeepEvalService/scripts/verify.ps1` | Ruff, strict mypy, and pytest pass |
| Six-Judge HTTP fake | enable `SEMANTIC_CROSS_SERVICE` and run `Phase2SemanticCrossServiceTest` | six independent PASS/FAIL results; timeout `UNAVAILABLE`; invalid output `ERROR` |
| Approved model | enable all three non-production real-model gates and run `Phase2SemanticRealModelAcceptanceTest` | PASS on 2026-08-27: six independent contract-valid `FAIL` decisions from approved `deepseek-chat`; 1 test, 0 failures/errors/skips; no secret/provider body retained |
| Contracts | `python contracts/validate_phase2_contracts.py` | all positive/negative fixtures conform |
| Architecture/security | SRE architecture and redaction tests plus forbidden scan | core remains framework-neutral; prohibited technology/secret patterns absent |
| OpenSpec | strict validation of `implement-phase2-governed-evaluation-data` | valid proposal/design/spec/task graph |
