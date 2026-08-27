## Context

See `proposal.md` for motivation. `InvestigationCoordinator.finalize(...)` currently updates status, inserts Conclusion and
finishes the Run through separate DAO calls without an explicit transaction. DPOMAgent uses typed MyBatis interfaces/XML,
Flyway migrations, Spring MVC and virtual threads; default verification must remain offline. The repository prohibits Kafka
and additional infrastructure, while the archived workspace contract requires RFC 8785 canonical JSON, SHA-256, stable
identity, explicit provenance and bounded delivery.

## Goals / Non-Goals

**Goals:**

- Make eligible terminalization and outbox creation one local MySQL transaction.
- Produce byte-stable Diagnosis Event v1 content from persisted facts, not transient LLM objects.
- Deliver outside the terminal transaction with safe concurrency, restart recovery and bounded retries.
- Make every transition operator-visible without exposing evidence or credentials.
- Keep delivery adapters replaceable and disabled in default/offline profiles.

**Non-Goals:**

- Implementing SRE Intelligence ingestion or changing the neutral event schema.
- Publishing non-terminal progress events or introducing a general event bus.
- Replaying the Investigation reasoning process; replay here means redelivering an immutable stored event.
- Sending FAILED/CANCELLED investigations to evaluation in v1.
- Adding automatic mitigation or a shared cross-service database.

## Decisions

### 1. Extract terminalization into one transactional service

`InvestigationCoordinator` will continue to decide the effective result and target status, then delegate persistence to a
focused terminalization service with a public Spring transaction boundary. The service will:

1. lock/re-read the Investigation and ensure the target transition is still legal;
2. update terminal status;
3. insert Conclusion;
4. finish the current InvestigationRun;
5. build the event exclusively from persisted/domain inputs plus validated configuration provenance;
6. insert one PENDING outbox row and append its CREATED audit row.

COMPLETED and INCONCLUSIVE are evaluation-eligible and use event type `investigation.completed`; FAILED and CANCELLED keep
their current behavior without an outbox event. A unique `(investigation_id, run_id, event_type)` key makes repeated
terminalization idempotent. Any participating write failure rolls back the transaction.

Alternative considered: annotate the entire investigation loop `@Transactional`. Rejected because LLM/tool/network calls
would hold a database transaction and locks for an unbounded duration.

### 2. Keep the canonical model in agent-common and persistence in agent-core

Transport-neutral Diagnosis Event records and the delivery port belong in `agent-common`; they contain only internal domain
types and never RestClient/HTTP DTOs. Canonical construction, validation, outbox state machine and MyBatis persistence belong
in `agent-core`. `agent-web` remains the composition root for scheduling, configuration, HTTP adapter and controller.

The neutral workspace JSON Schema and fixtures are copied into test resources with a recorded source SHA-256. A contract
test fails if Java serialization diverges from positive fixtures or accepts the negative fixture mutations. Production code
does not depend on an absolute `D:\code` path.

### 3. Use a standards-compliant RFC 8785 canonicalizer

Canonical content is generated from Jackson's tree form through a small RFC 8785 library isolated behind
`CanonicalJsonWriter`. The dependency version is pinned and subjected to the repository's license/security review. Contract
tests include Unicode, integer and supported numeric vectors; non-finite numbers and values the library cannot represent
canonically fail before outbox insertion.

The canonical UTF-8 bytes are size-checked, hashed with SHA-256 and persisted as immutable MEDIUMTEXT plus a 64-character
lowercase hex hash. Delivery rehashes stored bytes before any network call.

Alternative considered: Jackson `ORDER_MAP_ENTRIES_BY_KEYS`. Rejected because key sorting alone is not full RFC 8785 number
and string canonicalization.

### 4. Add V12 outbox, audit and replay-nonce tables

The implementation reserves `V12__diagnosis_event_outbox.sql` after the current V11 migration. The main table contains:

- numeric id; unique eventId and idempotencyKey;
- investigationId, runId, eventType, aggregateSequence and unique investigation/run/type key;
- schemaVersion, canonical MEDIUMTEXT and canonical SHA-256;
- PENDING/IN_FLIGHT/DELIVERED/DEAD state, attempt count, next attempt time;
- lease owner, opaque lease token and lease expiry for fencing;
- last stable error code, delivered/created/updated timestamps.

`diagnosis_event_audit` is append-only and stores event id, action, result, stable error code, bounded operator reference,
correlation id and timestamp, never event content. `diagnosis_event_replay_nonce` stores HMAC nonces through their validity
window so replay protection survives restart. All SQL remains in MyBatis XML with explicit columns/result maps and typed
commands; both MySQL 8 and H2 MySQL-mode tests are required.

### 5. Lease with optimistic compare-and-set and fencing token

A worker selects a bounded page of ready IDs, then attempts an atomic conditional update from PENDING (or expired IN_FLIGHT)
to IN_FLIGHT while assigning workerId, UUID lease token and lease expiry. Only the successful updater owns the event.
Completion/retry updates include id + status + lease token + unexpired ownership predicates, preventing a stale worker from
overwriting a recovered attempt.

This avoids database-specific `SKIP LOCKED` behavior while remaining safe for the initial single-instance deployment and
future concurrent workers.

### 6. Use deterministic bounded retry and explicit outcome mapping

Configuration supplies maxAttempts, maxEventAge, baseDelay, maxDelay, leaseDuration, connect/read timeout and batch size.
Delay is exponential and capped; deterministic jitter is derived from eventId and attempt count so restart does not create a
retry storm or change test results.

The HTTP adapter maps a bounded JSON acknowledgement to the internal `DeliveryAcknowledgement`:

- ACCEPTED / EQUIVALENT_DUPLICATE: DELIVERED;
- RETRYABLE_FAILURE, timeout, 408, 429 and 5xx: PENDING with next attempt;
- PERMANENT_REJECTION and non-retryable 4xx: DEAD;
- IDEMPOTENCY_CONFLICT: DEAD with the original event preserved.

Unknown/malformed/oversized acknowledgement bodies fail closed as retryable only while the retry budget remains. Delivery
sends `Content-Type: application/json`, event identity/idempotency headers and an HMAC signature over timestamp, method,
path and body hash using a server-side secret. Secrets and query parameters are never logged.

### 7. Make assembly fail closed and disabled by default

`dpom.evaluation.delivery.enabled=false` is the default. When disabled, no scheduler, HTTP adapter or replay controller is
assembled; outbox persistence remains available so enabling delivery later does not require regenerating diagnoses. When
enabled, HTTPS destination, strong outbound HMAC secret and all positive bounds are validated at startup.

A separate `dpom.evaluation.replay.enabled=false` gate controls the internal replay controller. Replay uses timestamped HMAC
headers and a persistent nonce; the request contains only eventId, bounded operatorRef and reason. Body fields beyond the
allow-list are rejected. This provides authentication without adding Spring Security or an external identity service.

### 8. Treat audit as transactional and metrics as best-effort

Every state mutation and its audit insert occur in the same transaction. If audit persistence fails, the state change rolls
back. Micrometer counters/gauges use only state, result and stable errorCode tags; metric failure does not alter business
state. Structured logs use eventId/correlationId but not incidentId, investigationId, content, evidence or secrets.

## Risks / Trade-offs

- [Terminalization refactor can regress the mature coordinator] → Add characterization tests first and keep reasoning/tool
  flow unchanged; only extract the final persistence block.
- [V12 number could collide with another unmerged migration] → Recheck the migration directory immediately before apply and
  renumber only the new unpublished migration if necessary.
- [RFC 8785 dependency introduces supply-chain surface] → Isolate one pinned library, review license/security metadata and
  retain contract vectors so it can be replaced behind the port.
- [Persistent canonical content contains diagnosis summaries] → Enforce the 64 KiB bound, reuse redaction checks, restrict
  database access and never copy content to audit/logs.
- [HTTP destination may be absent during this change] → Keep delivery disabled by default and use a fake port for all default
  tests; real E2E remains explicitly enabled.
- [Operator HMAC is not enterprise identity] → Scope it to an internal, separately gated endpoint and retain operatorRef plus
  nonce audit; replace the adapter when the enterprise identity plane is available.

## Migration Plan

1. Add characterization tests around eligible/ineligible terminalization and current rollback behavior.
2. Add V12 tables, typed MyBatis persistence and MySQL/H2 contract tests without enabling delivery.
3. Add canonical model/serializer and pass the shared positive/negative fixtures.
4. Extract transactional terminalization, backfill no historical events, and verify new terminal cases create one PENDING row.
5. Add lease/retry state machine, audit and metrics behind disabled configuration.
6. Add HTTP/HMAC adapter and separately gated replay endpoint; validate fail-closed startup.
7. Deploy with delivery/replay disabled, verify migrations and outbox creation, then enable delivery only after the SRE
   Intelligence ingestion contract is deployed.

Rollback disables both gates first. Runtime code can then roll back while retaining V12 tables; migrations are not reversed
and stored events remain available for a later compatible deployment.
