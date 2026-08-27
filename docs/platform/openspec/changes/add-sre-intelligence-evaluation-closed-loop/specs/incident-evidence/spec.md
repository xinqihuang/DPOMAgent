# incident-evidence Specification

## Purpose

Define trustworthy, bounded evidence lineage across Huawei Cloud telemetry, CMDB, codegraph, and OBS artifacts.

## Requirements

### Requirement: Evidence manifest

Every evidence item SHALL have a stable identity, evidence type, producer, collection time/range, source system, content digest, schema version, sensitivity classification, retention class, and correlation lineage. Large content SHALL be referenced through a controlled OBS artifact rather than embedded without bound.

#### Scenario: Evidence is attached to a diagnosis

- **WHEN** DPOMBaseMCPServer records cloud, topology, log, trace, metric, or code evidence
- **THEN** it SHALL produce a manifest whose digest and source metadata allow SRE to verify lineage
- **AND** provider credentials and temporary authorization material SHALL be excluded

### Requirement: Artifact integrity and access boundary

OBS references SHALL use allow-listed locations, bounded size and media type, checksum verification, retention metadata, and least-privilege access. SRE SHALL store metadata and derived bounded projections, not Huawei Cloud credentials.

#### Scenario: Artifact checksum does not match

- **WHEN** retrieved artifact bytes do not match the manifest digest
- **THEN** the evidence SHALL be marked integrity-failed
- **AND** dependent Gold promotion or required evaluation SHALL fail closed

### Requirement: Immutable provenance

Evidence provenance SHALL be append-only. A corrected or re-collected item SHALL receive a new version or identity and SHALL retain supersession lineage.

#### Scenario: Source evidence is corrected

- **WHEN** an operator supplies a corrected artifact or metadata record
- **THEN** the prior evidence SHALL remain queryable
- **AND** all cases and runs SHALL continue to reference the exact version they consumed

### Requirement: Topology and code context versions

CMDB topology snapshots and codegraph references SHALL bind to collection time and source/repository revision. A later topology or repository change MUST NOT alter historical reconstruction.

#### Scenario: A case is replayed after a deployment

- **WHEN** current topology or code differs from the original incident
- **THEN** replay SHALL use the frozen topology and code-context references from the case version

### Requirement: Redaction and minimization

Evidence SHALL be classified and minimized before curation or evaluation. Secrets, credentials, personal data, and unrestricted raw payloads MUST NOT be sent to DeepEval.

#### Scenario: Evidence contains a secret pattern

- **WHEN** validation detects prohibited sensitive content
- **THEN** the item SHALL be rejected, quarantined, or replaced by an approved redacted version
- **AND** the original sensitive content SHALL not appear in logs, metrics, or judge requests

### Requirement: Broken-reference behavior

Missing, expired, unauthorized, or deleted required artifacts SHALL produce an explicit unavailable state. The platform MUST NOT infer that prior derived claims remain valid without the required evidence.

#### Scenario: Required artifact is unavailable during replay

- **WHEN** a replay cannot verify a required evidence artifact
- **THEN** affected judge and aggregate outcomes SHALL be INCOMPLETE or BLOCKING according to policy

