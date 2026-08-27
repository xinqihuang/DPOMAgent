# Phase 1B Kafka cutover and HTTP rollback

## Authority invariant

DPOMAgent remains the only Investigation/Diagnosis authority before, during and after transport changes.
The immutable `authority_diagnosis_source` and its frozen `authority_publication_intent` are never regenerated,
deleted or transferred to DPOMBaseMCPServer or SRE Intelligence Service. HTTP and Kafka are delivery adapters only.

## Defaults and admission epoch

- DPOMAgent publication is default-off: `DPOM_EVALUATION_DELIVERY_ENABLED=false`.
- SRE Kafka ingestion is default-off: `SRE_KAFKA_INGESTION_ENABLED=false`.
- `DPOM_AUTHORITY_EPOCH` must equal SRE's configured active epoch.
- `DPOM_KAFKA_PRODUCER_IDENTITY` must be in SRE's accepted v2 producer list and must also be configured as
  `SRE_KAFKA_EXPECTED_PRODUCER_IDENTITY`.
- Topic is fixed by contract to `dpom.diagnosis-event.v2`; bootstrap servers are environment configuration.
- Never put broker credentials, HMAC secrets, canonical bodies or evidence bodies in logs or runbook evidence.

## Pre-cutover gates

1. Apply `db/deployment/authority-realignment/004_publication_outbox_forward.sql` with admission disabled.
2. Reconcile every legacy row before making the new frozen columns non-null. Verify canonical SHA-256 values and
   source digests without printing canonical content.
3. Verify local/target broker topic configuration, retention, partition count and ACLs against `contracts/kafka`.
4. Run DPOMAgent H2 and dedicated real-MySQL authority contracts, SRE HTTP/Kafka conformance tests, duplicate,
   conflict, sequence-gap, quarantine and replay tests.
5. Confirm `/actuator/health` exposes `authorityPublication` with backlog below capacity and no unexplained DEAD
   records. Confirm SRE consumer readiness, quarantine capacity and lag are within the approved policy.

## Controlled cutover

1. Enable SRE Kafka consumption for the approved epoch and producer while DPOMAgent delivery remains disabled.
2. Set DPOMAgent `DPOM_EVALUATION_DELIVERY_MODE=KAFKA`, configure bootstrap servers and producer identity, then set
   `DPOM_EVALUATION_DELIVERY_ENABLED=true`.
3. Observe at least one complete compatibility window. Reconcile by event ID, idempotency key, canonical digest,
   source digest and aggregate sequence; do not compare database row IDs across services.
4. Treat equivalent redelivery as success. Treat a reused identity with different digest, sequence gaps,
   quarantine growth, exhausted retries or sustained lag as a cutover violation.

## Rollback to authenticated HTTP

1. Disable DPOMAgent publication, wait for active leases to expire, and retain all Outbox and attempt history.
2. Disable SRE Kafka admission for the affected epoch. Do not delete topic records or SRE receipts.
3. Configure the existing HTTPS destination and HMAC secret, set `DPOM_EVALUATION_DELIVERY_MODE=HTTP`, then enable
   delivery. The authority worker reads the same frozen v2 Outbox records; no diagnosis or event is regenerated.
4. Keep SRE's authenticated HTTP compatibility endpoint enabled for the approved window and reconcile projections
   using the same identities and digests.
5. Re-enable Kafka only under a new approved admission decision after lag, capacity, conflict and quarantine causes
   are resolved. Changing transport never changes diagnosis ownership.

## Recovery and replay

- Broker outage and uncertain send remain PENDING until bounded retry or DEAD; process restart recovers expired
  fencing leases and resends identical key/content.
- Replay is allowed only from DEAD through the authenticated operator boundary. It resets delivery attempts but
  preserves canonical content, SHA-256, topic, idempotency key, source link and prior attempt history.
- A content-integrity failure is never replayed until the stored record and source are investigated.
