# Phase 4 authority Outbox and transport evidence

Date: 2026-08-27

## Implemented

- Terminal authority commit freezes Diagnosis Event v2 canonical JSON, SHA-256, topic, idempotency key, aggregate
  sequence, source digest, epoch and producer identity in the same transaction as the immutable diagnosis source.
- The authority Outbox uses short transactions, fencing leases, bounded batch/retry/age policies, immutable
  redelivery, append-only attempt history, expired-lease recovery, idempotent acknowledgement and DEAD replay.
- Delivery is default-off and explicitly selects one adapter: authenticated HTTPS or Kafka. Kafka uses `acks=all`,
  idempotent producer mode, one in-flight request per connection, Investigation partition key and required v2
  headers. HTTP and Kafka receive the same frozen record.
- Diagnosis Progress v1.1 admission and subsequent authority transitions are frozen in the same transaction as
  each authority audit record. Progress publication has an independent default-off switch, fixed
  `dpom.diagnosis-progress.v1` topic, RFC 8785 canonical bytes, a deterministic progress id, exact aggregate
  version and strict per-Investigation sequence fencing. Legacy Investigations without sequence-1 admission stay
  on authenticated REST/SSE and are never inserted into Kafka mid-stream.
- The Progress worker uses short database transactions, fencing leases, bounded capacity/retry/age policy,
  immutable replay and append-only attempt history. Broker calls happen outside transactions; DEAD or missing
  earlier rows block later sequence delivery until an operator replays the original frozen record.
- SRE's existing HTTP controller and Kafka listener both map validated events through
  `DiagnosisEventCommandFactory` and `TransactionalDiagnosisEventIngestion`, while transport metadata is stored
  separately from canonical domain content.
- Capacity health reports only bounded state counts. No canonical body, evidence content, request header or secret
  is used as a metric/health label.

## Verification

- Focused store plus H2 authority contract: 20 tests, 0 failures/errors.
- Dedicated real MySQL authority contract: 17 tests, 0 failures/errors.
- DPOMAgent full Maven suite: 523 tests, 0 failures/errors, 46 conditional skips.
- Local Kafka 4.3.1 Progress contract: 1 test, 0 failures/errors. The test obtains a real broker
  acknowledgement and consumes the exact key, required headers and canonical bytes from
  `dpom.diagnosis-progress.v1` on `localhost:9092`.
- Restart test proves broker failure followed by a new worker instance retains byte-identical canonical content and
  digest, then records exactly one delivered authority projection state.
- DEAD replay test proves retry budget reset without regenerating canonical content or discarding attempt history.
- The shared H2/MySQL contract proves admission at aggregate version 0, restart recovery, strict ordered delivery,
  fail-closed idempotency conflict, atomic rollback on Progress intent conflict, and no synthetic mid-stream
  Progress for legacy Investigations.
- SRE focused HTTP/Kafka ingestion matrix: 22 tests, 0 failures/errors. It proves compatible HTTP status/ack fields,
  Kafka acknowledgement only after durable accepted/duplicate/gap state, conflict quarantine before acknowledgement,
  transient persistence failure without acknowledgement, idempotent restart, conflicting duplicate preservation,
  per-Investigation gap promotion, bounded reconciliation and equivalent HTTP/Kafka authority projection.
- SRE real Kafka 4.3.1 plus MySQL 8.0 joint contract: 1 test, 0 failures/errors. It proves broker redelivery after a
  committed database transaction, consumer restart without duplicate authority, ordered gap promotion, poison
  quarantine, HTTP/Kafka race equivalence, immutable operator replay, retired-authority fail-closed behavior,
  quarantine capacity recovery and authenticated HTTP compatibility rollback. The gate used a dedicated
  `sre_kafka_contract` schema and did not reuse a production schema.
- Failure/restart coverage additionally proves terminal state is committed before publication, broker outage keeps
  frozen bytes pending, an uncertain Progress send whose acknowledgement loses its lease is recovered as an
  equivalent duplicate, expired leases are fenced, retry exhaustion records `RETRY_EXHAUSTED` and blocks later
  Progress sequence, immutable operator replay releases that sequence, and publisher/consumer restarts create no
  duplicate authority projection.

The complete local DPOMAgent-to-Kafka-to-SRE rehearsal remains tracked under task 7.3; this evidence does not claim
that gate is complete.
