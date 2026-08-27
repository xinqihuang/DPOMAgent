## Why

DPOMAgent already persists the authoritative Investigation lifecycle, but completed diagnoses are not durably handed to the
evaluation control plane. A transactional outbox is required so a terminal diagnosis and its canonical Diagnosis Event are
committed atomically, survive restarts, and can be retried without Kafka or regeneration from the original LLM session.

## What Changes

- Persist a Diagnosis Event v1 outbox row in the same transaction that writes the terminal investigation status,
  Conclusion, and InvestigationRun completion.
- Build the event from persisted domain facts and explicit provenance, conforming to the neutral workspace JSON Schema and
  fixtures under `D:\code\contracts\diagnosis-event\v1`.
- Add bounded delivery states, leases, exponential retry, terminal failure visibility, and operator-triggered replay while
  preserving the original event identity and canonical content.
- Add an HTTP delivery port and Spring RestClient adapter whose acknowledgement semantics distinguish accepted, equivalent
  duplicate, conflicting duplicate, retryable failure, and permanent rejection.
- Keep delivery disabled by default and preserve offline `mvn clean verify`; no Kafka, new infrastructure, automatic
  remediation, or cross-service database access is introduced.

## Capabilities

### New Capabilities

- `diagnosis-event-outbox`: Covers atomic event creation, canonical serialization, durable outbox lifecycle, leased retry,
  delivery acknowledgement, replay, audit, metrics, and fail-closed configuration.

### Modified Capabilities

- `investigation-agent`: Extends terminal Investigation persistence so status, Conclusion, run completion, and the canonical
  diagnosis event are committed atomically and remain recoverable after restart.

## Impact

- `agent-core`: terminalization transaction boundary, canonical event model/serializer, outbox domain service, typed
  persistence interfaces and delivery port.
- `agent-web`: Flyway migration, MyBatis XML mapper wiring, RestClient adapter, conditional configuration, scheduler and
  operator replay endpoint.
- Tests: schema/fixture conformance, transaction rollback, concurrent lease, retry/restart, duplicate acknowledgement,
  security/logging, H2 default verification, and real MySQL contract coverage where already supported.
- External contract: neutral Diagnosis Event v1 remains authoritative; this change produces it but does not modify the
  SRE Intelligence consumer or DeepEval.
