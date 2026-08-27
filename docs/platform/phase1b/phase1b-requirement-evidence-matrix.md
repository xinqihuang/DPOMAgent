# Phase 1B requirement-to-evidence matrix

> Historical matrix for the superseded DPOMBase-owned Phase 1B decision. See the active OpenSpec realignment
> tasks and evidence for current requirements.

Every delta-spec scenario is mapped below. Test class names are repository-searchable and reports are under
`docs/phase1b`.

| Spec scenario(s) | Primary evidence |
|---|---|
| Boundary: online cloud evidence; evaluation requests production evidence; orchestration proposed in gateway | `ServiceBoundaryArchitectureTest`, `verify-phase1-service-boundaries.ps1`, `architecture-boundary-gates.md` |
| Boundary: runtime resumes; evaluation consumes completion; compatibility event during migration | `InvestigationPersistenceTest`, `InvestigationTerminalizationServiceTest`, `ConcurrentIngestionTest`, `sre-unified-ingestion-report.md` |
| Boundary: nightly evaluation; diagnosis progress displayed | existing SRE suite coordinators plus `InvestigationProgressControllerTest`, `dpombase-progress-report.md` |
| Boundary: service needs foreign data | architecture tests reject cross-service DB/SDK/persistence dependencies |
| Boundary: target/end-to-end completion; Phase 1 completion evaluated | `evidence/local-kafka-mysql-contract-2026-08-25.json`, `evidence/cutover-rehearsal-2026-08-25.json`, `evidence/phase1-final-acceptance-2026-08-25.json` |
| Ingestion: valid Kafka; valid signed HTTP; invalid producer/auth; invalid/replayed signature | `DiagnosisEventKafkaListenerTest`, `DiagnosisEventEndpointTest`, `HmacRequestVerifierTest` |
| Ingestion: first delivery; equivalent redelivery; conflicting redelivery; concurrent duplicate | `TransactionalIngestionTest`, `ConcurrentIngestionTest`, `KafkaMySqlContractIT` |
| Ingestion: compatible acknowledgement; Kafka durable success; transient DB failure | `DiagnosisEventEndpointTest`, `DiagnosisEventKafkaListenerTest`, `KafkaMySqlContractIT` |
| Ingestion: offline verification; real MySQL verification | SRE `mvn clean verify`; `DiagnosisIngestionMySqlContractIT`; `KafkaMySqlContractIT` |
| Kafka: stop before send | `PublicationMessagingTest`, `InvestigationMySqlContractIT`, `dpombase-kafka-publication-report.md` |
| Kafka: unsupported major version; sequence gap; consumer stops before ack | `DiagnosisEventConformanceTest`, `QuarantineReconciliationTest`, `KafkaMySqlContractIT` |
| Kafka: digest conflict; authorized replay; capacity exhausted | `DiagnosisEventKafkaListenerTest`, `KafkaQuarantineServiceTest`, `OperatorReplayService` tests, `sre-kafka-intake-report.md` |
| Contract: terminal durable authority; inactive epoch; HTTP/Kafka parity; fixture corpus | `InvestigationTerminalizationServiceTest`, `SourceAuthorityRegistryTest`, `TransportParityTest`, `validate_phase1b_contracts.py` |
| Runtime: create; stale command; restart; uncertain external call; tool budget | `InvestigationPoliciesTest`, `InvestigationPersistenceTest`, `InvestigationMySqlContractIT` |
| Runtime: eligible completion; failed/cancelled terminalization | `InvestigationTerminalizationServiceTest`, `InvestigationPersistenceTest` |
| Runtime: Portal resume; slow-client capacity | `InvestigationProgressControllerTest`, `ProgressWindow`, `dpombase-progress-report.md` |
| Runtime: incomplete cutover; compatibility rollback | `AuthorityActivationPropertiesTest`, `SourceAuthorityRegistryTest`, `LegacyAuthorityRetirementTest`, `authority-cutover-runbook.md` |

ADR Phase 1 exit criteria map to the same evidence: three-service ownership and no cross-database access use
the boundary scan; durable DPOMBase runtime uses domain/persistence reports; immutable Kafka delivery uses both
Kafka reports; SRE/DeepEval closed loop uses the real cross-service test; Portal progress uses the API snapshot;
controlled retirement is proven by the external local-Kafka cutover/rollback evidence and the fail-closed retirement checks.
