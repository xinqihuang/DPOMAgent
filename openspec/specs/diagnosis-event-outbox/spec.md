# Diagnosis Event Outbox Specification

## Purpose

Provides a durable, idempotent and observable handoff of terminal DPOMAgent diagnosis facts to the evaluation control plane
without coupling the domain contract to a broker or requiring regeneration from an LLM conversation.

## Requirements

### Requirement: Canonical Diagnosis Event conformance
DPOMAgent SHALL persist and deliver Diagnosis Event v1 content that conforms to the neutral workspace schema, positive
fixtures, size limits, provenance rules, security boundary, and inline-payload/artifact-reference exclusivity.

#### Scenario: Terminal diagnosis produces an event
- **GIVEN** an Investigation reaches a supported terminal outcome with a persisted Conclusion and Run
- **WHEN** DPOMAgent creates the diagnosis handoff
- **THEN** the event SHALL validate against Diagnosis Event v1
- **AND** its incidentId, investigationId and runId SHALL identify the authoritative persisted records

#### Scenario: Required provenance is unavailable
- **GIVEN** a component version cannot be determined from persisted facts or configuration
- **WHEN** DPOMAgent builds the event
- **THEN** it SHALL record an explicit unavailable reason defined by the contract
- **AND** it MUST NOT invent, omit or use an empty value for that dimension

### Requirement: Transactional outbox creation
DPOMAgent SHALL commit the terminal Investigation status, Conclusion, Run completion and immutable outbox event in one local
database transaction. It MUST NOT publish to a network destination inside that transaction.

#### Scenario: Terminal transaction commits
- **GIVEN** finalization inputs and canonical event content are valid
- **WHEN** the terminal transaction commits
- **THEN** the terminal domain records and exactly one pending outbox row SHALL become visible together

#### Scenario: Outbox insert fails
- **GIVEN** the outbox row cannot be persisted
- **WHEN** terminalization attempts to commit
- **THEN** the Investigation status, Conclusion and Run completion changes SHALL roll back
- **AND** no terminal Investigation without its required event SHALL be visible

### Requirement: Immutable event identity and content
An outbox event SHALL retain its eventId, idempotencyKey, aggregateSequence, schemaVersion, canonical content and canonical
content hash for its entire lifetime. Retries and operator replay MUST NOT regenerate or mutate those values.

#### Scenario: Delivery is retried
- **GIVEN** a pending event has already failed a delivery attempt
- **WHEN** it is attempted again
- **THEN** DPOMAgent SHALL send byte-equivalent canonical content with the original identity and hash

#### Scenario: Content mutation is detected
- **GIVEN** persisted content does not match its recorded canonical hash
- **WHEN** delivery or replay loads the event
- **THEN** the event SHALL fail closed with an integrity error
- **AND** no network request SHALL be made

### Requirement: Durable bounded delivery lifecycle
The outbox SHALL expose PENDING, IN_FLIGHT, DELIVERED and DEAD states with attempt count, next-attempt time, lease owner,
lease expiry, last stable error code and delivery timestamp. Attempt count and retry horizon MUST be bounded by configuration.

#### Scenario: Event is ready for delivery
- **GIVEN** a PENDING event has reached its next-attempt time and is below retry limits
- **WHEN** a worker acquires it
- **THEN** it SHALL transition atomically to IN_FLIGHT with a bounded lease

#### Scenario: Retry budget is exhausted
- **GIVEN** an event has reached its configured attempt or age limit
- **WHEN** another retry would otherwise occur
- **THEN** it SHALL transition to DEAD with a stable reason
- **AND** it SHALL remain queryable and eligible only for explicit operator replay

### Requirement: Concurrent lease safety and recovery
At most one worker SHALL own an unexpired lease for an event. An expired IN_FLIGHT lease SHALL be recoverable after process
restart without losing the event or allowing an old owner to mark a newer attempt delivered.

#### Scenario: Workers race for one event
- **GIVEN** multiple workers select the same ready event concurrently
- **WHEN** they attempt to acquire a lease
- **THEN** exactly one worker SHALL become the active owner

#### Scenario: Worker stops during delivery
- **GIVEN** an event remains IN_FLIGHT after its lease expires
- **WHEN** a later worker performs recovery
- **THEN** it SHALL return the event to an eligible retry state
- **AND** a stale lease owner MUST NOT complete the recovered attempt

### Requirement: Delivery acknowledgement semantics
The delivery boundary SHALL classify outcomes as ACCEPTED, EQUIVALENT_DUPLICATE, RETRYABLE_FAILURE, PERMANENT_REJECTION or
IDEMPOTENCY_CONFLICT using stable error codes. ACCEPTED and EQUIVALENT_DUPLICATE SHALL mark the event DELIVERED;
IDEMPOTENCY_CONFLICT MUST fail closed and MUST NOT be retried automatically.

#### Scenario: Consumer accepts an equivalent duplicate
- **GIVEN** the consumer already stored the same idempotency key and canonical content
- **WHEN** it returns EQUIVALENT_DUPLICATE
- **THEN** DPOMAgent SHALL mark the event DELIVERED without creating a new identity

#### Scenario: Consumer is temporarily unavailable
- **GIVEN** a connection timeout, bounded server failure or explicit retryable acknowledgement occurs
- **WHEN** the attempt completes
- **THEN** DPOMAgent SHALL schedule a bounded exponential retry with jitter
- **AND** a timeout MUST NOT be treated as accepted

#### Scenario: Consumer reports an idempotency conflict
- **GIVEN** the consumer reports the same idempotency key with different canonical content
- **WHEN** DPOMAgent records the acknowledgement
- **THEN** the event SHALL transition to DEAD with IDEMPOTENCY_CONFLICT
- **AND** its original content SHALL remain unchanged for audit

### Requirement: Explicit operator replay
An authenticated internal operator action SHALL allow a DEAD event to return to PENDING only after validating the stored
content and preserving its original identity. The action SHALL be separately audited and MUST NOT accept replacement content.

#### Scenario: Valid dead event is replayed
- **GIVEN** a DEAD event has valid canonical content and hash
- **WHEN** an authenticated operator requests replay with a bounded reason
- **THEN** the event SHALL return to PENDING with retry counters reset according to policy
- **AND** eventId, idempotencyKey, aggregateSequence and canonical content SHALL remain unchanged

#### Scenario: Caller supplies replacement content
- **GIVEN** an operator replay request includes event or payload content
- **WHEN** DPOMAgent validates the request
- **THEN** it SHALL reject the request
- **AND** the persisted event SHALL remain unchanged

### Requirement: Fail-closed configuration and offline defaults
Network delivery and the operator replay surface SHALL be disabled by default. Enabling delivery SHALL require a bounded
HTTPS destination and complete timeout/retry configuration; invalid configuration MUST fail application startup.

#### Scenario: Default application starts
- **GIVEN** no evaluation delivery configuration or external service is available
- **WHEN** the default application or test profile starts
- **THEN** no delivery worker or replay endpoint SHALL be assembled
- **AND** offline `mvn clean verify` SHALL not contact a network destination

#### Scenario: Enabled configuration is incomplete
- **GIVEN** delivery is enabled without a valid HTTPS destination or required bounds
- **WHEN** the application starts
- **THEN** startup SHALL fail with a stable configuration error

### Requirement: Audit, metrics and log safety
Event creation, lease, attempt, acknowledgement, retry, DEAD transition, recovery and operator replay SHALL emit append-only
audit records and low-cardinality metrics. Logs, metrics and audit MUST NOT include canonical event bodies, evidence content,
credentials, destination query parameters, incident IDs or investigation IDs as metric labels.

#### Scenario: Delivery attempt fails
- **GIVEN** a delivery attempt returns an error
- **WHEN** DPOMAgent records the outcome
- **THEN** audit SHALL contain event type, result, stable error code, event identifier and timestamp
- **AND** metrics SHALL use only bounded state/result/error labels
- **AND** logs and audit SHALL not contain the event body or credentials

### Requirement: No broker or production execution expansion
The outbox capability MUST NOT introduce Kafka, another broker, Knowledge/RAG, arbitrary shell execution, production write
tools, automatic mitigation, or direct access to another service's database.

#### Scenario: Outbox delivery is enabled
- **GIVEN** the outbox worker is active
- **WHEN** its runtime dependencies are inspected
- **THEN** delivery SHALL use only the configured diagnosis-event delivery port
- **AND** existing DPOMAgent safety boundaries SHALL remain unchanged
