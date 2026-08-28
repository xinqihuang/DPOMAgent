## Purpose

Defines DPOMAgent as the durable and replayable authority for the complete diagnosis lifecycle while keeping evidence providers and evaluation services outside that state boundary.

## ADDED Requirements

### Requirement: Authoritative Investigation state
DPOMAgent SHALL be the sole system of record for Incident, Investigation, Investigation Run, Investigation Step, Observation, Hypothesis, Conclusion, budgets, ToolUse records and diagnosis audit history. State transitions MUST be durable, append-only where historical meaning is required, optimistic-concurrency safe and restartable.

#### Scenario: Investigation resumes after interruption
- **WHEN** DPOMAgent restarts while an Investigation is non-terminal
- **THEN** it SHALL reconstruct the exact authoritative state and continue without creating a second authority or silently repeating a completed step

#### Scenario: Another service attempts to persist diagnosis state
- **WHEN** DPOMBaseMCPServer, SRE Intelligence Service or DeepEval Service is configured as an Investigation state store
- **THEN** architecture verification SHALL fail and the service MUST NOT start with that ownership

### Requirement: Evidence-bound ToolUse history
Every tool decision and result used by diagnosis SHALL retain the tool contract version, bounded arguments metadata, target scope, correlation identity, status, timing and immutable evidence references. Credentials, raw provider envelopes and unrestricted evidence bodies MUST NOT enter Investigation state, logs or progress events.

#### Scenario: Evidence tool succeeds
- **WHEN** DPOMAgent invokes a DPOMBase evidence tool successfully
- **THEN** the Investigation SHALL record the selected tool/version and returned evidence references before a dependent conclusion becomes terminal

#### Scenario: Tool call is unavailable
- **WHEN** an evidence tool times out, rejects scope or returns an integrity failure
- **THEN** DPOMAgent SHALL retain an explicit non-success ToolUse outcome and MUST NOT fabricate the missing evidence

### Requirement: Durable terminal diagnosis
A terminal diagnosis SHALL atomically persist its conclusion disposition, supporting observations/evidence references, alternatives, evidence gaps, component provenance and canonical source digest before it becomes eligible for external publication.

#### Scenario: Terminal commit succeeds
- **WHEN** all required terminal invariants are satisfied
- **THEN** DPOMAgent SHALL persist one immutable terminal source projection and make the corresponding publication intent eligible in the same transaction

#### Scenario: Terminal commit fails
- **WHEN** persistence or invariant validation fails
- **THEN** no Diagnosis Event, progress terminal marker or final report SHALL be published

### Requirement: Bounded progress and diagnosis source APIs
DPOMAgent SHALL expose authenticated bounded progress and immutable diagnosis-source queries for Portal and report consumers. Responses MUST contain stable identities, states, timestamps, counts, dispositions and references but MUST exclude prompts, chain-of-thought, credentials, raw model output and evidence bodies.

#### Scenario: Portal requests progress
- **WHEN** an authorized Portal client requests an active Investigation
- **THEN** DPOMAgent SHALL return or stream ordered progress from authoritative state without requiring DPOMBase to synthesize lifecycle events

