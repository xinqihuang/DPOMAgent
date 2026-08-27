# Phase 3 Requirement-to-Evidence Matrix

| Requirement | Objective evidence |
|---|---|
| Versioned taxonomy and deterministic attribution | `FailureTaxonomyCatalogTest`, `DeterministicFailureAttributorTest`, `FailureAttributionServiceTest`, `contracts/phase3/v1` |
| Human confirm/reject/correct and immutable reconstruction | `FailureAttributionReviewPolicyTest`, `FailureAttributionServiceTest`, `Phase3GovernancePersistenceTest` |
| Restartable bounded attribution and gap work | `AttributionMaterializationServiceTest`, `GapMaterializationServiceTest`; interruption/restart, capacity, idempotency and equal digest assertions |
| Comparable confirmed capability gaps with counterexamples | `CapabilityGapCalculatorTest`, `CapabilityGapServiceTest`, `Phase3GovernanceEndToEndTest` |
| Advisory-only recommendation lifecycle | `RecommendationGovernanceTest`, `RecommendationGovernanceServiceTest`, `FailureAttributionControllerTest`; `/execute` is absent |
| Frozen compatible candidate comparison | `ReleaseGovernanceTest`, `ReleaseGovernanceServiceTest`; exact report/cohort/component versions and ordered case/Judge deltas |
| Fail-closed Release Gate | `ReleaseGovernanceTest`; explicit FAIL, UNKNOWN, MISSING, UNAVAILABLE and INCOMPATIBLE predicates all produce BLOCK |
| Immutable decisions, supersession and waiver | `ReleaseGovernanceServiceTest`, `Phase3GovernanceMySqlContractIT`; original BLOCK remains unchanged across grant, expiry and revoke |
| Secure bounded APIs/contracts/telemetry | `FailureAttributionControllerTest`, `Phase3ContractValidationTest`, `CoreArchitectureTest`; fixed reason/status/policy tags only |
| Real infrastructure compatibility | `Phase3GovernanceMySqlContractIT` on MySQL 8.0/3306 and `Phase2EndToEndMySqlContractIT` on MySQL, Kafka 9092 and DeepEval fixture 18081 |

All named tests are executable without production credentials. Real-infrastructure tests require explicit non-production mutation gates and runtime-injected secrets.
