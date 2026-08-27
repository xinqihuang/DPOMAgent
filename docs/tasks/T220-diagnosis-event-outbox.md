# T220 — Diagnosis Event transactional outbox

## Goal

Persist a contract-conformant Diagnosis Event v1 atomically with eligible Investigation terminalization, then deliver the
immutable event through a disabled-by-default, leased, bounded and auditable HTTP outbox without Kafka or another service's
database.

## In Scope

- COMPLETED and INCONCLUSIVE terminalization transaction; FAILED/CANCELLED remain event-free.
- Neutral Diagnosis Event v1 model, explicit provenance, RFC 8785 canonical bytes and SHA-256.
- MySQL/H2 outbox, append-only audit and persistent replay-nonce tables through typed MyBatis XML.
- PENDING/IN_FLIGHT/DELIVERED/DEAD lifecycle, fencing lease, bounded retry and expired-lease recovery.
- Replaceable delivery Port, RestClient/HMAC adapter, conditional scheduler and HMAC-protected operator replay.
- Offline default tests plus explicitly gated real MySQL contract verification.

## Out of Scope

- SRE Intelligence ingestion implementation, DeepEval, Kafka or another broker.
- Non-terminal progress events and historical event backfill.
- Knowledge/RAG/Embedding/Vector DB, arbitrary shell, production write tools or automatic mitigation.
- Direct access to another service's database or changing Diagnosis Event v1.

## Inputs

- `openspec/changes/add-dpomagent-diagnosis-event-outbox/{proposal,design,tasks}.md`
- `openspec/changes/add-dpomagent-diagnosis-event-outbox/specs/**/spec.md`
- `D:\code\contracts\diagnosis-event\v1` at the source hash recorded in test resources
- `docs/architecture/ADR-003-ai-sre-service-boundary-reference.md`
- Existing `InvestigationCoordinator`, typed DAO/XML conventions and MySQL baseline contract

## Deliverables

- `agent-common`: transport-neutral event/provenance/artifact/acknowledgement records and delivery port.
- `agent-core`: canonical writer/builder, transactional terminalizer, outbox/audit/nonce persistence, lease/retry/delivery and
  replay services.
- `agent-web`: next unpublished Flyway migration, conditional properties/composition, RestClient/HMAC adapter, scheduler and
  internal replay endpoint.
- Test resources: pinned schema/fixtures/source manifest; Java conformance, persistence, transaction, lease, adapter,
  configuration, replay-auth and safety tests.
- Documentation: README/config examples and implementation handoff.

## Test-First Order

1. Characterize existing COMPLETED/INCONCLUSIVE/FAILED terminalization.
2. Pin the shared contract and write canonicalization/conformance tests.
3. Write migration and typed persistence tests before DAO/XML implementation.
4. Write atomic commit/rollback/concurrency tests before extracting terminalization.
5. Write lease/retry/delivery tests before the worker and HTTP adapter.
6. Write fail-closed configuration and replay-HMAC tests before conditional web assembly.
7. Run focused tests after each slice, then offline `mvn clean verify`, strict OpenSpec and gated real MySQL checks.

## Security and Reliability Pitfalls

- Never perform network I/O inside the terminalization transaction.
- Never regenerate eventId, idempotencyKey, sequence or content on retry/replay.
- Rehash persisted canonical bytes before delivery; integrity mismatch makes no network request.
- Fence every completion/update with the active lease token so stale workers cannot overwrite recovered attempts.
- HMAC secrets, signatures, query strings, event bodies, evidence and credentials never enter logs/audit/metrics.
- Metric labels stay limited to bounded state/result/error values; no incident/investigation/event IDs as tags.
- Replay accepts only eventId/operatorRef/reason and persists nonce validity across restart.
- Default/test startup must assemble no delivery worker, HTTP adapter or replay endpoint.

## Acceptance

- Eligible terminal status, Conclusion, Run completion, one PENDING event and CREATED audit commit or roll back together.
- FAILED/CANCELLED terminalization creates no evaluation event.
- Java output passes both positive and all negative Diagnosis Event v1 fixtures and records exact canonical SHA-256.
- Unique keys and racing terminalization/leases produce one event and one active lease owner.
- Expired lease recovery is safe; stale tokens cannot complete; retry limits end in queryable DEAD state.
- ACCEPTED/EQUIVALENT_DUPLICATE deliver; retryable failures reschedule; permanent/conflict outcomes fail closed.
- Operator replay preserves identity/content, validates hash/HMAC/nonce and appends audit atomically.
- Delivery/replay are disabled by default; invalid enabled configuration fails startup.
- H2 tests pass offline; real MySQL tests only report `REAL_EXECUTED` after actual MySQL 8 execution.
- No Kafka/RAG/shell/automatic remediation/cross-service database dependency appears.
- `mvn clean verify`, strict OpenSpec validation and checkstyle pass.
