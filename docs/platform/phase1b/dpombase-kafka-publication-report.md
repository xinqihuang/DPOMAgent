# Phase 1B DPOMBase Kafka Publication Verification

> Historical evidence for a superseded ownership boundary; it is not current Kafka authority or acceptance proof.

Date: 2026-08-25

## Scope

This report closes OpenSpec tasks 5.1 through 5.5 for DPOMBase post-commit Kafka publication.

## Canonical records

- `DiagnosisEventV2Builder` derives the event identity, aggregate sequence/version, source-authority epoch,
  immutable publication-intent identity, evidence reference, provenance, and payload from persisted terminal
  facts. It emits RFC 8785 canonical bytes and a lowercase SHA-256 digest.
- `ProgressV1Builder` derives its independent progress sequence, aggregate version, authority, status, stage,
  and checkpoint data from a persisted progress record.
- Contract tests reproduce the shared Diagnosis Event v2 and Diagnosis Progress v1 positive fixtures at the
  canonical-byte level; transport metadata remains outside those bytes.
- Inline and total record bounds fail closed before persistence or broker send.

## Durable at-least-once delivery

Publication intent storage now freezes topic, canonical bytes, and digest exactly once. The MyBatis XML
adapter leases only eligible records, gives every attempt a new fencing token, recovers expired leases,
rejects stale acknowledgements, applies capped exponential backoff, and moves exhausted attempts to a
terminal failure state. Attempts, age, batch size, lease time, backoff, and admission capacity are all
bounded. Broker I/O occurs only after the lease transaction has completed.

The Kafka adapter accepts only `dpom.diagnosis-event.v2` and `dpom.diagnosis-progress.v1`, keys every record
by Investigation identity, enforces the pre-compression record bound, emits five bounded headers, and uses a
configured producer identity. Producer settings require all acknowledgements and enable Kafka idempotence,
while correctness remains based on durable at-least-once delivery and consumer idempotency.

## Replay, readiness, and observability

Operator replay has an explicit authentication precondition and accepts only the original intent identity;
no replacement bytes or digest enter the API. An eligible replay re-admits the frozen record and appends the
bounded operator reference and reason to service-local audit.

Publication is disabled by default. Enabling it requires valid persistence, broker endpoints, producer and
worker identities, time bounds, and capacity settings. Invalid configuration fails with a constant safe
message. Capacity-aware readiness exposes only state/backlog/capacity. Metrics use fixed outcome labels and
logs use stable reason codes; none include event bodies, evidence bodies, identifiers, exception payloads,
or credentials.

## Deployment and verification evidence

- Fresh deployments receive the publication delivery columns in release `001`; databases provisioned from
  the earlier Investigation-only form apply `002_publication_delivery_forward.sql` once.
- Offline persistence suite: 8 tests, 0 failures, 0 errors, 0 skipped.
- Messaging suite: 6 tests, 0 failures, 0 errors, 0 skipped.
- Kafka adapter verification uses Kafka `MockProducer` to prove fixed topics, Investigation key, bounded
  headers, acknowledgement flow, and byte preservation without requiring a broker in the default build.
- Gated MySQL 8.0.46 contract: 1 integration test, 0 failures, status `EXECUTED`; it covers the incremental
  SQL, freeze, lease, stale fence rejection, acknowledgement, and immutable audited replay.
- Full `mvn verify` in `DPOMBaseMCPServer`: PASS across all 13 reactor modules.
- Full Surefire aggregate: 110 suites, 485 tests, 0 failures, 0 errors, 0 skipped.
- Checkstyle: 0 violations in every reactor module.
- Phase 1 service-boundary scan: PASS.

Kafka publication remains default-off; real broker delivery and restart/redelivery acceptance are scheduled
for the gated Kafka suite in OpenSpec task 7.5. Runtime credentials were never persisted or reported.
