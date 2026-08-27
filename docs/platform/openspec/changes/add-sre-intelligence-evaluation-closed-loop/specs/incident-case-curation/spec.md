# incident-case-curation Specification

## Purpose

Define immutable Incident Case Versions and the governed Bronze/Silver/Gold workflow that creates trusted evaluation ground truth.

## Requirements

### Requirement: Immutable Incident Case Version

An Incident Case Version SHALL freeze normalized incident facts, evidence/reconstruction references, expected diagnosis labels where available, provenance, schema version, curation-policy version, and canonical digest. Published content MUST NOT be updated in place.

#### Scenario: Case content is corrected

- **WHEN** a curator changes any frozen field
- **THEN** the platform SHALL create a new case version with supersession lineage
- **AND** prior datasets and runs SHALL retain the old version

### Requirement: Bronze curation

Bronze SHALL represent machine-curated cases that pass schema, lineage, size, and minimum-source checks. Bronze status MUST NOT imply human-verified ground truth.

#### Scenario: Required source lineage is missing

- **WHEN** automatic curation cannot resolve the diagnosis event or evidence lineage
- **THEN** the case SHALL not enter Bronze
- **AND** a stable curation failure shall be recorded

### Requirement: Silver validation

Silver SHALL require structural completeness, evidence accessibility/integrity, reconstruction validity, and policy checks. Validation jobs MUST NOT silently repair or invent source facts.

#### Scenario: Evidence expires during validation

- **WHEN** a required artifact cannot be verified
- **THEN** Silver promotion SHALL fail with an explicit reason

### Requirement: Gold human review

Gold SHALL require an authenticated human decision against an exact case version and review-policy version. The record SHALL include reviewer, outcome, reason, timestamp, and optional corrected labels through a new case version.

#### Scenario: Reviewer approves current version

- **WHEN** required reviewer policy and all checks pass
- **THEN** an append-only approval SHALL promote that exact version to Gold

#### Scenario: Stale reviewer approves superseded version

- **WHEN** the reviewed version is no longer current for the requested promotion
- **THEN** the command SHALL fail with a conflict and SHALL not promote the newer version

### Requirement: Review separation and audit

Automated jobs and the Future Improvement Agent MUST NOT approve Gold. Review decisions, reversals, and superseding decisions SHALL be immutable and queryable.

#### Scenario: Gold approval is revoked

- **WHEN** authorized governance revokes approval
- **THEN** a new decision SHALL supersede the approval
- **AND** historical dataset/run evidence SHALL retain the decision state that applied when frozen

### Requirement: Safe query and progress

Case APIs and SSE progress SHALL be bounded, authorized, newest-first where historical, and free of credentials and unrestricted evidence bodies.

#### Scenario: Portal requests curation progress

- **WHEN** an authorized user watches a batch
- **THEN** SSE SHALL expose safe counts, states, and stable error classes rather than raw evidence

