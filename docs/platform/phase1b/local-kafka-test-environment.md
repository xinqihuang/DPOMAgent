# Local Kafka test environment

- Distribution: Apache Kafka 4.3.1 (Scala 2.13)
- Installation: `D:\kafka_2.13-4.3.1`
- Mode: single-node KRaft, local non-production only
- Bootstrap server: `127.0.0.1:9092`
- Controller listener: `127.0.0.1:9093`
- Persistent test data: `D:\kafka_2.13-4.3.1\data\phase1b`
- Configuration: `D:\code\scripts\local-kafka\server.properties`

Start and initialize the broker from PowerShell:

```powershell
& 'D:\code\scripts\local-kafka\start-local-kafka.ps1'
& 'D:\code\scripts\local-kafka\initialize-topics.ps1'
```

Stop only the broker managed by this configuration:

```powershell
& 'D:\code\scripts\local-kafka\stop-local-kafka.ps1'
```

The fixed topics are `dpom.diagnosis-event.v2` and `dpom.diagnosis-progress.v1`, each with two partitions
and replication factor one. Auto-topic creation is disabled so a misspelled contract topic fails closed.

The external Kafka + MySQL contract is gated. It runs only when the existing MySQL test variables plus
`SRE_KAFKA_TEST_BOOTSTRAP_SERVERS=127.0.0.1:9092` and
`SRE_KAFKA_TEST_ALLOW_MUTATION=true` are supplied. The test truncates existing records on the diagnosis-event
topic, so this broker must never contain shared or production data.

The first external run completed on 2026-08-25 with broker 4.3.1, Kafka client 3.8.1, MySQL 8.0, and
2 Failsafe ITs passing with no failures, errors, or skips. Evidence is stored in
`docs/phase1b/evidence/local-kafka-mysql-contract-2026-08-25.json`.

Embedded Kafka remains acceptable for isolated unit/test-fixture coverage only. It cannot be cited as real
broker, deployment, cutover, rollback, or Phase 1 completion evidence.
