# diagnosis-kafka-delivery Specification

## Purpose
Defines reliable DPOMAgent publication of canonical diagnosis and progress records to Kafka while preserving the accepted HTTP compatibility path and rollback safety.

## Requirements

### Requirement: State-before-publication outbox
DPOMAgent SHALL create immutable Diagnosis Event and progress publication intents only from durable authoritative state. The canonical payload, digest, aggregate sequence, topic identity and idempotency key MUST be frozen transactionally before delivery.

#### Scenario: Diagnosis becomes terminal
- **WHEN** DPOMAgent commits a terminal diagnosis
- **THEN** one eligible outbox record SHALL reference that exact terminal source digest and Kafka delivery MAY begin only after the commit succeeds

#### Scenario: Broker is unavailable
- **WHEN** Kafka delivery times out or fails
- **THEN** authoritative diagnosis state and frozen outbox content SHALL remain durable for bounded retry without regenerating the diagnosis

### Requirement: Idempotent ordered Kafka delivery
The publisher SHALL use per-Investigation ordering, stable event identity and bounded retry/lease generations. Equivalent redelivery MUST be idempotent; a reused identity with different canonical content MUST fail closed and become operationally visible.

#### Scenario: Publisher restarts after an uncertain send
- **WHEN** a worker loses its lease after sending but before recording acknowledgement
- **THEN** retry SHALL use identical key/content and SRE ingestion SHALL produce one authoritative projection

### Requirement: HTTP and Kafka parity
The Phase 1A authenticated HTTP adapter and Kafka consumer SHALL invoke the same SRE ingestion application policy. Equal canonical events MUST yield equivalent validation, acknowledgement, idempotency, conflict, quarantine, audit and projection outcomes.

#### Scenario: Event is delivered through both transports
- **WHEN** the same canonical event is delivered by compatibility HTTP and Kafka
- **THEN** SRE Intelligence Service SHALL retain one projection and expose equivalent recorded outcomes for both deliveries

### Requirement: Controlled cutover and rollback
Kafka publication and consumption SHALL be default-off until contract, capacity, lag, replay and parity gates pass. Cutover MUST use explicit admission epochs, observe a compatibility window and support rollback to HTTP without changing DPOMAgent diagnosis ownership or deleting records.

#### Scenario: Kafka cutover is rolled back
- **WHEN** lag, conflict rate, readiness or reconciliation violates the approved policy
- **THEN** operators SHALL disable Kafka admission/publication, retain all outbox/audit history and continue the compatibility path from the same authoritative diagnosis records

### Requirement: Secret-safe delivery observability
Metrics, readiness and audit SHALL expose bounded delivery states, lag, attempts, conflict classes and stable correlation identities. They MUST NOT expose canonical bodies, evidence content, credentials, prompts or model responses.

#### Scenario: Delivery repeatedly fails
- **WHEN** an outbox record exhausts its retry policy
- **THEN** readiness and audit SHALL show a stable terminal delivery reason and replay eligibility without logging the payload body
