# evaluation-datasets Specification

## Purpose

Define reproducible Dataset Versions built from exact Incident Case Versions and governed through an explicit lifecycle.

## Requirements

### Requirement: Immutable Dataset Version

A Dataset Version SHALL freeze exact case-version membership, membership order or ordering rule, tier composition, selection-policy version, split assignment, schema versions, creation provenance, and canonical cohort digest.

#### Scenario: Dataset is approved

- **WHEN** review checks pass
- **THEN** approval SHALL freeze membership and digest
- **AND** later case or policy changes SHALL not mutate the version

### Requirement: Dataset lifecycle

The supported lifecycle SHALL be DRAFT, REVIEW, APPROVED, ACTIVE, DEPRECATED, and ARCHIVED. Transitions SHALL be authorized, validated, optimistic-concurrency safe, and append-only audited.

#### Scenario: Draft is submitted for review

- **WHEN** membership, lineage, tier, split, and integrity checks pass
- **THEN** the dataset MAY enter REVIEW

#### Scenario: Archived dataset is selected for a new run

- **WHEN** a caller requests new evaluation against ARCHIVED data
- **THEN** the request SHALL be rejected unless a specific governed replay policy permits it

### Requirement: Gold and exception policy

Release-critical datasets SHALL contain Gold case versions unless a fixed policy explicitly permits a bounded non-Gold cohort. Exceptions SHALL be visible in the Dataset Version and gate policy.

#### Scenario: Unapproved case enters release dataset

- **WHEN** membership includes a case that violates the required tier
- **THEN** approval SHALL fail with a stable reason

### Requirement: Split integrity and leakage prevention

Selection SHALL prevent prohibited overlap across train/development/evaluation partitions using stable incident, signature, topology, or policy-defined grouping keys.

#### Scenario: Related case crosses forbidden splits

- **WHEN** leakage validation finds a prohibited grouping overlap
- **THEN** dataset approval SHALL fail and expose bounded conflict references

### Requirement: Reproducible materialization

Spring Batch materialization SHALL be bounded, restartable, and idempotent. Partial jobs MUST NOT publish an approved or active version.

#### Scenario: Materialization restarts

- **WHEN** a job resumes from a persisted checkpoint
- **THEN** completed members SHALL not be duplicated
- **AND** final digest SHALL equal a clean run over the same frozen inputs

### Requirement: Deprecation and retention

Deprecation SHALL stop default selection for new runs while preserving historical replay and lineage. Archive/retention actions SHALL account for evidence and decision retention dependencies.

#### Scenario: Dataset supports an audit-held gate decision

- **WHEN** retention would remove required case or evidence lineage
- **THEN** archival SHALL preserve the referenced records until the hold expires

