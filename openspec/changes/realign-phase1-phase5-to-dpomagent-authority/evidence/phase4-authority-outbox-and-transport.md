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
- SRE's existing HTTP controller and Kafka listener both map validated events through
  `DiagnosisEventCommandFactory` and `TransactionalDiagnosisEventIngestion`, while transport metadata is stored
  separately from canonical domain content.
- Capacity health reports only bounded state counts. No canonical body, evidence content, request header or secret
  is used as a metric/health label.

## Verification

- Focused H2 authority/transport suite: 16 tests, 0 failures/errors.
- Dedicated real MySQL authority contract: 11 tests, 0 failures/errors.
- DPOMAgent full Maven suite: 233 tests, 0 failures/errors, 38 conditional skips.
- Restart test proves broker failure followed by a new worker instance retains byte-identical canonical content and
  digest, then records exactly one delivered authority projection state.
- DEAD replay test proves retry budget reset without regenerating canonical content or discarding attempt history.

Kafka progress publication and the complete local DPOMAgent-to-Kafka-to-SRE failure matrix remain tracked under
tasks 4.2, 4.5, 4.6 and 7.3; this evidence does not claim those gates are complete.
