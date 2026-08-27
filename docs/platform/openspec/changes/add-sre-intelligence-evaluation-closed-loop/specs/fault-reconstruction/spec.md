# fault-reconstruction Specification

## Purpose

Define reproducible reconstruction of fault sources and causal chains from immutable incident and evidence facts.

## Requirements

### Requirement: Versioned reconstruction input

A reconstruction run SHALL freeze the Incident identity, input event/evidence versions, topology snapshot, code-context revision, algorithm/rule version, and configuration digest.

#### Scenario: Reconstruction starts

- **WHEN** all required normalized inputs are available
- **THEN** SRE SHALL persist the frozen input manifest before producing derived fault facts

### Requirement: Fault source candidates

Fault source output SHALL distinguish observed facts, candidate sources, confidence, supporting evidence, contradicting evidence, and unresolved ambiguity. A candidate MUST NOT be represented as confirmed ground truth without human review.

#### Scenario: Two services remain plausible

- **WHEN** evidence supports multiple fault-source candidates
- **THEN** reconstruction SHALL retain both candidates and their evidence
- **AND** it SHALL mark the result ambiguous rather than selecting an unsupported winner

### Requirement: Fault-chain graph

A fault chain SHALL be a bounded directed graph of versioned nodes and edges. Each causal edge SHALL reference supporting evidence or an explicit inference rule and SHALL prohibit cycles unless the declared schema supports and labels feedback behavior.

#### Scenario: Unsupported causal edge is proposed

- **WHEN** an edge has neither evidence nor an approved inference rule
- **THEN** it SHALL be rejected or marked unverified
- **AND** it SHALL not satisfy Gold or release-critical completeness

### Requirement: Deterministic reprocessing

Equal frozen input and reconstruction version SHALL produce an equivalent normalized output digest. Reprocessing with a new version SHALL create a new reconstruction record and preserve lineage.

#### Scenario: Algorithm version changes

- **WHEN** a case is reconstructed with a newer algorithm
- **THEN** both outputs SHALL remain queryable and comparable
- **AND** historical case versions SHALL continue to reference their original reconstruction

### Requirement: Bounded failure states

Missing topology, missing required evidence, invalid graph, budget exhaustion, or transformation error SHALL have stable non-success states and reason codes.

#### Scenario: Topology snapshot is absent

- **WHEN** topology is required by the active reconstruction policy but unavailable
- **THEN** reconstruction SHALL be INCOMPLETE
- **AND** the platform SHALL not manufacture a complete fault chain

### Requirement: Separation from semantic judging

Fault reconstruction SHALL produce evaluation inputs and factual/inferred projections. It MUST NOT use an LLM judge result as source ground truth or allow DeepEval to persist reconstruction state.

#### Scenario: DeepEval scores a reconstructed chain

- **WHEN** FaultChainJudge evaluates the chain
- **THEN** its result SHALL be stored as an evaluation result separate from the reconstructed chain

