# SRE Kafka intake, quarantine, and reconciliation report

Status: implementation and core external local-broker contract accepted; extended acceptance pending.

SRE consumes only `dpom.diagnosis-event.v2`, validates the configured producer, canonical bytes,
digest, publication intent, investigation key, source authority, record age, and bounded headers, then
delegates to the same transport-neutral ingestion command used by HTTP. Manual acknowledgement occurs
only after a durable accepted/duplicate/gap receipt or a durable permanent-failure quarantine row.
Database failures remain unacknowledged and use a bounded retry policy.

Permanent failures are stored by topic/partition/offset with stable reason codes and body-safe metadata.
Only an exact canonical record quarantined for a recoverable transport-age condition can be replayed;
operator query and replay require a strong token, have bounded fields/page sizes, preserve frozen content,
and append replay identity, reason, and time. Open depth is capacity-gated and appears in readiness and
low-cardinality metrics.

Evidence:

- Offline SRE `mvn clean verify`: 150 tests, zero failures/errors, four gated skips.
- Real MySQL 8.0.46 plus embedded Kafka 3.8.1: test-fixture evidence only.
- The gated contract requires an explicit external bootstrap server and separate Kafka mutation consent.
  Against local Kafka 4.3.1 at `127.0.0.1:9092`, both MySQL Failsafe ITs passed with 0 failures/errors/skips;
  `KAFKA_MYSQL_CONTRACT_STATUS=EXECUTED broker=external`.
- The real-broker contract proves commit-before-ack redelivery, two-partition ordering, gap promotion, poison
  quarantine/acknowledgement, and recovery. Cross-transport race, authorized replay, and capacity exhaustion
  still require external-broker coverage before OpenSpec task 7.5 closes.
- The broker test covers commit-before-ack redelivery, two partitions, sequence gap and promotion,
  poison durable quarantine and acknowledgement; offline concurrency/capacity tests cover cross-transport
  racing, transient no-ack, capacity exhaustion and reconciliation recovery.
- Deployment release maps immutable V1-V6 to explicit SQL; verification returned
  `SRE_DEPLOYMENT_SQL_VERIFY=PASS`; rollback refuses open quarantine work or v2 receipts.
- HTTP compatibility endpoint accepts both preserved v1 and canonical v2 while keeping transport metadata
  outside identity and authority.
