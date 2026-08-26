## ADDED Requirements

### Requirement: Transport-neutral canonical delivery
DPOMAgent SHALL deliver the same immutable canonical Diagnosis Event through either the compatibility HTTP adapter or the
Kafka adapter. Transport envelopes MUST NOT change eventId, idempotencyKey, aggregateSequence, schemaVersion, canonical
content or canonical content hash.

#### Scenario: The same event is delivered by both adapters
- **GIVEN** an immutable pending outbox event and dual-delivery validation is enabled
- **WHEN** HTTP and Kafka adapters deliver that event
- **THEN** both deliveries SHALL contain canonically equivalent Diagnosis Event content and identity
- **AND** adapter-specific metadata MUST remain outside the canonical event

### Requirement: Kafka delivery acknowledgement
Kafka publication SHALL mark an outbox attempt delivered only after the broker acknowledges the configured topic and the
producer result satisfies the bounded delivery policy. Timeout, authorization failure, serialization failure and broker
unavailability MUST NOT be inferred as accepted.

#### Scenario: Broker acknowledgement is unavailable
- **GIVEN** a worker publishes an eligible event to Kafka
- **WHEN** the acknowledgement times out or returns an error
- **THEN** DPOMAgent SHALL classify the attempt with a stable retryable or permanent error
- **AND** it MUST NOT mark the event DELIVERED without an accepted acknowledgement

### Requirement: Reversible HTTP to Kafka cutover
The primary delivery transport SHALL be selected by validated configuration. HTTP retirement MUST occur only after transport
parity, backlog drain, replay, observability and rollback acceptance pass for the configured compatibility window.

#### Scenario: Kafka becomes primary
- **GIVEN** parity tests and operational gates have passed
- **WHEN** an operator switches the primary transport from HTTP to Kafka
- **THEN** new eligible events SHALL use Kafka without changing canonical event semantics
- **AND** the operator SHALL be able to restore HTTP without rewriting persisted events

#### Scenario: Compatibility window ends
- **GIVEN** Kafka has remained healthy for the documented compatibility window and HTTP backlog is empty
- **WHEN** HTTP retirement is approved
- **THEN** only the HTTP delivery adapter SHALL be disabled or removed
- **AND** DPOMAgent and its authoritative Investigation records SHALL remain active

## MODIFIED Requirements

### Requirement: No broker or production execution expansion
The outbox capability MAY introduce Kafka solely as a diagnosis-event transport adapter. It MUST NOT introduce another
broker, Knowledge/RAG, arbitrary shell execution, production write tools, automatic mitigation or direct access to another
service's database.

#### Scenario: Outbox delivery is enabled
- **GIVEN** the outbox worker is active
- **WHEN** its runtime dependencies are inspected
- **THEN** delivery SHALL use only an enabled diagnosis-event delivery adapter through the existing delivery port
- **AND** Kafka dependencies SHALL remain outside the domain and persistence policy
- **AND** existing DPOMAgent production-execution safety boundaries SHALL remain unchanged

