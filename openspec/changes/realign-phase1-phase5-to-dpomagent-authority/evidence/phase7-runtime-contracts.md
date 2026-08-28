# Phase 7 Runtime Contract Evidence

Date: 2026-08-28 (Asia/Shanghai)

## Exact component commits

- DPOMAgent diagnosis authority and producer: `f867f95`.
- SRE evaluated-report consumer: `9b8c242`.
- DPOMBase evidence-only tool service: `fd08e6d`.
- HuaweiCloudAlarmChangeGuard mutation service: `1250ed6`.
- DeepEval semantic-Judge service and repository-local contract: `fc57486`.
- Full clean-clone results and test totals are recorded in `isolated-contract-builds.md`.

## DPOMAgent authority persistence on real MySQL

- Target: local MySQL 8.0 on `127.0.0.1:3306`, dedicated schema `dpom_authority_contract`.
- Gate: `AuthorityRealMysqlPersistenceContractTest` with explicit mutation opt-in.
- Result: 20 tests executed, 0 failures, 0 errors, 0 skipped.
- Covered: insert and uniqueness, idempotency, optimistic locking and concurrent terminalization,
  bounded paging, transaction rollback, immutable histories, exact reconstruction and restart-like reads.
- Credentials were entered through a secure prompt and were not written to commands, files or evidence.

## Kafka broker restart and persistence

- Broker: local Kafka 4.3.1 on `127.0.0.1:9092`, KRaft data directory configured by
  `D:/code/scripts/local-kafka/server.properties`.
- Before restart, a unique marker was acknowledged in topic `dpom.acceptance.broker-restart.v1`.
- The exact validated `kafka.Kafka` process owning port 9092 was stopped and restarted with the same
  configuration. The broker and controller recovered on ports 9092 and 9093.
- After restart, `kafka-console-consumer` read the exact pre-restart marker from retained storage.
- Kafka 4.3.1 on this Windows host requires `KAFKA_HEAP_OPTS` to be set because `wmic` is absent;
  the successful restart used `-Xmx1G -Xms1G` without changing broker configuration or data.

## DPOMAgent producer and SRE consumer contracts

- DPOMAgent `DiagnosisEventLocalKafkaContractTest` and
  `DiagnosisProgressLocalKafkaContractTest`: 2 tests executed against the external broker, 0 failures.
  The Diagnosis Event test uses the repository-owned v2 fixture, RFC 8785 bytes and SHA-256, and verifies
  the real broker acknowledgement, investigation key, authority headers and exact retained bytes.
- SRE `KafkaMySqlContractIT`, rerun after the actual broker restart against local MySQL: 1 test executed,
  0 failures. Status: `KAFKA_MYSQL_CONTRACT_STATUS=EXECUTED broker=external`.
- Covered downstream behavior: equivalent duplicates, conflicting/retired authority, ordering gaps,
  poison quarantine, immutable replay, quarantine capacity recovery, cross-transport race and HTTP
  compatibility rollback (`AUTHORITY_ROLLBACK_STATUS=EXECUTED transport=HTTP_COMPATIBILITY`).

## Phase 2 to Phase 5 real infrastructure chain

- SRE `Phase2EndToEndMySqlContractIT` ran against external Kafka, real MySQL and the local fake-model
  DeepEval endpoint.
- Result: 1 test executed, 0 failures; status
  `PHASE5_E2E_CONTRACT_STATUS=EXECUTED kafka=PASS mysql=PASS judges=7 report=PASS`.
- Covered: Kafka ingestion, dataset/replay preparation, expired Judge lease recovery, seven Judge results,
  aggregate evaluation, evaluated report generation, RFC 8785 deterministic replay and Markdown rendering.
- The same real-MySQL profile executed the Phase 3 and Phase 4 governance contracts with zero failures;
  the full offline SRE reactor regression also passed (SRE core 101 tests; SRE web 253 tests, 6 gated skips).
- Phase 2's separate approved-model gate passed on 2026-08-27 under explicit non-production approval using the
  current fixed six-Judge catalog and DeepSeek-compatible `deepseek-chat`. All six calls returned bounded,
  contract-valid `FAIL` decisions (scores 0.0-0.4 at threshold 0.8); `FAIL` is a valid evaluation result and no
  missing/unavailable/error result was promoted. The Maven gate ran 1 test with 0 failures/errors/skips.
- The runtime-only API key and service token were entered through an interactive process environment, cleared after
  execution and were not written to commands, files, evidence or Git. SRE owns the detailed body-free evidence in
  `docs/phase2-approved-model-acceptance-2026-08-27.md`.
- The initial run exposed an assertion overload that compared Jackson object iteration order. The gate now
  compares RFC 8785 canonical bytes, matching the contract's deterministic semantic requirement; the
  corrected test passed.

## Phase 5 cross-service acceptance

- DPOMAgent and SRE `diagnostic-report/v1` trees were compared at the exact commits above: all twelve assets
  matched, and both repositories' pinned SHA-256 manifests verified.
- DPOMAgent diagnosis-only contracts covered deterministic source projection, idempotency, optimistic
  revisions, immutable recovery lineage, exact history and real-MySQL rollback.
- SRE focused Phase 5 suites covered immutable DPOM lineage, individual-Judge/Dataset/Replay/Suite lineage,
  canonical replay, authorization/redaction and JSON/Markdown/Portal/HTML/PDF semantic equivalence.
- The exact-commit clean-clone reactor builds reran those suites with zero failures. The cross-service gate is
  therefore complete without reading another repository's source or database at runtime.
