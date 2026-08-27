# DPOMBase authoritative REST/SSE/Kafka progress report

> Historical evidence for a superseded ownership boundary. DPOMAgent now owns authoritative progress and SSE.

Status: accepted on 2026-08-25.

The default-off Portal API reads the existing MyBatis Investigation aggregate and persisted progress log.
It exposes only bounded identifiers, stable status/stage/summary codes, aggregate/progress sequence, authority
epoch, and timestamps. It never projects budgets, credentials, prompts, raw model output, evidence bodies,
or exception messages.

`GET /api/v1/investigations/{id}` returns the authoritative snapshot. The progress endpoint uses an exclusive
`after` cursor and bounded `limit`. SSE accepts `Last-Event-ID`, emits the persisted monotonic sequence as the
event id, sends heartbeats, and is bounded by per-read buffer, client semaphore, poll interval, and connection
duration. A retention gap produces an explicit `RETENTION_GAP` resynchronization result; the Portal then reads
the snapshot. Slow/disconnected clients consume only a bounded virtual thread and never mutate diagnosis state.

`PersistedProgressKafkaPublisher` reads the same `ProgressPort` window and `AuthorityEpoch`, builds immutable
Progress v1 canonical bytes, and publishes with the investigation key. Re-reading an earlier cursor after
restart regenerates the same progress identity/content/digest, providing safe at-least-once redelivery.

Evidence:

- Controller tests cover authorization, pagination bounds, safe DTO fields, retention gaps, SSE resume,
  capacity exhaustion, absent mutation routes, and REST/SSE/Kafka sequence/state parity.
- Persistence tests cover oldest/latest retention bounds and restart-readable progress.
- Kafka projection tests prove topic, canonical source, and progress sequence parity.
- DPOMBase `mvn verify`: 493 tests, zero failures/errors/skips after this change set.
- Gated MySQL contract: `MYSQL_CONTRACT_STATUS=EXECUTED`, schema version 1 state `READY`.
