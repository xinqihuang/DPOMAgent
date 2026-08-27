# incident-ingestion Specification

## Purpose

Define how SRE Intelligence durably and safely receives authoritative diagnosis facts from DPOMBaseMCPServer while preserving the Phase 1A HTTP compatibility path during Phase 1B.

## Requirements

### Requirement: Versioned diagnosis-event contract

DPOMBaseMCPServer SHALL publish a bounded Diagnosis Event containing stable event, incident, investigation, and source-run identities; schema version; occurrence and publication times; source component versions; canonical content digest; and evidence references. Provider credentials and provider SDK DTOs MUST NOT appear in the event.

#### Scenario: Supported event is published

- **WHEN** an authoritative diagnosis state reaches an eligible publication point
- **THEN** DPOMBaseMCPServer SHALL publish the supported immutable event version only after source state is durable
- **AND** the event SHALL contain sufficient lineage for downstream replay without the original LLM conversation

#### Scenario: Unsupported version arrives

- **WHEN** SRE receives an unknown major schema version
- **THEN** it SHALL quarantine the event with a stable reason
- **AND** it MUST NOT create or update downstream facts

### Requirement: Durable at-least-once ingestion

SRE SHALL validate and durably store an ODS receipt before acknowledging Kafka delivery. Processing SHALL tolerate at-least-once delivery and process restart.

#### Scenario: Consumer stops before acknowledgement

- **WHEN** the consumer restarts after durable receipt but before Kafka acknowledgement
- **THEN** redelivery SHALL resolve idempotently to the existing receipt
- **AND** no duplicate Incident Case or evaluation work SHALL be created

### Requirement: Identity and digest idempotency

An event identity SHALL bind to exactly one canonical digest. Equal identity and digest SHALL be idempotent; equal identity with a different digest SHALL fail closed.

#### Scenario: Equivalent redelivery occurs

- **WHEN** an already accepted identity is delivered with the same digest
- **THEN** SRE SHALL return or record the prior acceptance outcome without duplicating derived records

#### Scenario: Conflicting redelivery occurs

- **WHEN** an already accepted identity is delivered with a different digest
- **THEN** SRE SHALL quarantine the conflict, emit a bounded audit/metric signal, and preserve the original fact

### Requirement: Ordering and gap handling

Ordering SHALL be scoped by the declared investigation/run partition and monotonic source sequence. Gaps or regressions MUST be explicit and MUST NOT be silently reordered into authoritative state.

#### Scenario: Sequence gap is observed

- **WHEN** an event sequence advances beyond the next expected position
- **THEN** SRE SHALL hold or quarantine dependent promotion according to bounded policy
- **AND** it SHALL expose the gap without acknowledging a complete projection

### Requirement: Transport compatibility

Kafka and the Phase 1 authenticated HTTP compatibility endpoint SHALL invoke the same ingestion policy and produce equivalent receipt, idempotency, quarantine, audit, and projection behavior.

#### Scenario: Migration parity is evaluated

- **WHEN** the same canonical fixture is submitted through both transports in non-production
- **THEN** the resulting accepted fact and digest SHALL be semantically equivalent
- **AND** transport-specific metadata SHALL not change domain identity

### Requirement: Safe observability

Ingestion telemetry SHALL use bounded labels and SHALL exclude credentials, full event bodies, evidence bodies, arbitrary identifiers, prompts, and raw model output.

#### Scenario: Invalid event is rejected

- **WHEN** validation fails
- **THEN** audit and metrics SHALL record only stable schema/validation reason classes and safe correlation references
