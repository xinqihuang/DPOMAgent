## 1. Task Card and Characterization

- [x] 1.1 Create `docs/tasks/T220-diagnosis-event-outbox.md` with scope, non-goals, inputs, file-level deliverables,
  test-first order, security pitfalls and independently verifiable Acceptance criteria.
- [x] 1.2 Add characterization tests for current COMPLETED, INCONCLUSIVE and FAILED terminalization, including Conclusion,
  Run completion and state transitions, before refactoring `InvestigationCoordinator`.
- [x] 1.3 Copy Diagnosis Event v1 schema and positive/negative fixtures into test resources with a source SHA-256 manifest;
  add a test that detects drift from the copied contract assets without requiring `D:\code` at runtime.

## 2. Canonical Event Model and Serialization

- [x] 2.1 Add transport-neutral Diagnosis Event, Provenance, inline payload, Artifact reference and delivery acknowledgement
  records/ports to `agent-common`, with Chinese Javadoc and no HTTP/provider DTO leakage.
- [x] 2.2 Review, pin and isolate an RFC 8785 canonicalization dependency behind `CanonicalJsonWriter`; add canonicalization
  vectors for key order, Unicode, integers and supported numeric values before implementing the writer.
- [x] 2.3 Implement canonical event construction from persisted Investigation/Conclusion/Run facts and validated provenance,
  enforcing schema version, required/unavailable dimensions, exclusive payload form and 64 KiB/16 KiB bounds.
- [x] 2.4 Add Java conformance tests that accept both positive fixtures, reject every negative fixture mutation with the
  expected stable error, and verify lowercase SHA-256 of the exact canonical UTF-8 bytes.

## 3. Outbox Persistence

- [x] 3.1 Recheck the highest unpublished Flyway version, then add the next migration for `diagnosis_event_outbox`,
  `diagnosis_event_audit` and `diagnosis_event_replay_nonce` with unique identity/sequence constraints and bounded columns.
- [x] 3.2 Add typed outbox/audit/nonce records, insert/update commands, DAO interfaces and explicit-column MyBatis XML
  result maps/queries; prohibit annotation SQL, `Map` parameters and `SELECT *`.
- [x] 3.3 Add H2 MySQL-mode persistence tests for insert/read, unique event/idempotency/terminal keys, state transitions,
  immutable content, audit append-only behavior and nonce uniqueness/expiry.
- [x] 3.4 Extend the existing real MySQL contract path to execute the migration and lease/update SQL, recording
  `REAL_EXECUTED` only when a real MySQL 8 instance actually ran the assertions.

## 4. Atomic Terminalization

- [x] 4.1 Add failing integration tests proving eligible terminalization commits status, Conclusion, Run completion, one
  PENDING event and CREATED audit atomically, while FAILED/CANCELLED outcomes create no evaluation event.
- [x] 4.2 Add rollback and concurrency tests proving an outbox/audit failure rolls back all terminal writes and repeated or
  racing terminalization produces exactly one immutable event.
- [x] 4.3 Implement the focused transactional terminalization service and delegate only the final persistence block from
  `InvestigationCoordinator`, preserving the existing reasoning/tool loop and method-size/module constraints.

## 5. Lease, Retry and Delivery

- [x] 5.1 Add state-machine tests for PENDING/IN_FLIGHT/DELIVERED/DEAD, optimistic lease races, fencing token enforcement,
  expired-lease recovery, max attempts and max event age.
- [x] 5.2 Implement bounded page selection and compare-and-set lease/recovery/update operations with injected Clock, UUID
  source and deterministic capped exponential jitter.
- [x] 5.3 Add delivery-service tests for ACCEPTED, EQUIVALENT_DUPLICATE, retryable timeout/408/429/5xx, permanent 4xx,
  malformed/oversized acknowledgements, IDEMPOTENCY_CONFLICT and pre-network content-integrity failure.
- [x] 5.4 Implement delivery orchestration, transactional audit/state updates and low-cardinality Micrometer metrics using a
  fake delivery port by default; verify event bodies, evidence, credentials and high-cardinality IDs never enter logs/tags.
- [x] 5.5 Implement the bounded RestClient HTTP/HMAC adapter and contract tests for headers, body hash/signature, HTTPS-only
  destination, timeouts, response mapping and secret-safe errors.

## 6. Conditional Assembly and Operator Replay

- [x] 6.1 Add configuration-binding/startup tests for delivery/replay disabled defaults, positive bounds, HTTPS destination,
  strong secrets and fail-closed incomplete enabled configuration.
- [x] 6.2 Implement conditional properties, scheduler/worker and composition-root wiring so default/test startup assembles no
  network adapter, scheduler or replay controller.
- [x] 6.3 Add HMAC replay-auth tests for timestamp window, persistent nonce reuse across restart, constant-time signature
  comparison, field allow-list, bounded operatorRef/reason and uniform authentication errors.
- [x] 6.4 Implement the separately gated internal replay endpoint and service; accept only eventId/operatorRef/reason, verify
  stored content/hash, preserve identity/content, reset policy counters and append the replay audit atomically.

## 7. Verification and Handoff

- [x] 7.1 Run focused module tests after each group, then run default offline `mvn clean verify` with no MySQL, Redis, LLM,
  CodeGraph, OBS or SRE Intelligence endpoint and resolve all checkstyle/test failures.
- [x] 7.2 Run strict OpenSpec validation, schema/fixture conformance, forbidden dependency/surface scans and migration
  checksum checks; confirm no Kafka/RAG/shell/automatic-remediation or cross-service database access was introduced.
- [x] 7.3 Run the gated real MySQL contract when an approved local MySQL 8 environment is available; otherwise keep the test
  explicitly skipped and report it as not executed rather than passing it by mock or static inspection.
- [x] 7.4 Update README/config examples and the implementation handoff with feature gates, operational states, stable error
  codes, replay procedure, validation commands, rollback-by-disable steps and the prerequisite for enabling delivery.
