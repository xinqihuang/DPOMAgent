## ADDED Requirements

### Requirement: DPOMAgent producer authority
Every authoritative Diagnosis Event and diagnosis progress record SHALL be produced from immutable DPOMAgent Investigation state. DPOMBaseMCPServer, SRE Intelligence Service and DeepEval Service MUST NOT originate or regenerate an authoritative diagnosis event.

#### Scenario: Evidence is collected by DPOMBase
- **WHEN** DPOMBase returns bounded evidence to DPOMAgent
- **THEN** only DPOMAgent MAY incorporate its reference into authoritative Investigation state and subsequently publish a Diagnosis Event

### Requirement: Producer-owned portable conformance
The canonical Diagnosis Event schemas, semantic rules, fixtures and canonicalization vectors SHALL be version controlled with DPOMAgent and published or vendored with explicit version and digest provenance for consumers. A consumer build MUST NOT load test sources from an unversioned sibling contracts directory.

#### Scenario: SRE validates producer events in a clean clone
- **WHEN** SRE contract tests run without a sibling DPOMAgent or workspace contracts directory
- **THEN** they SHALL validate the supported producer contract version from repository-owned fixtures or a pinned versioned artifact

