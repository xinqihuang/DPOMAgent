# agent-replay-evaluation Specification

## Purpose

Define reproducible, restartable replay evaluation using deterministic Java rules, six stateless semantic judges, human-agreement evidence, and authoritative reports.

## Requirements

### Requirement: Frozen Replay Plan

A Replay Plan SHALL bind an immutable Dataset Version, candidate component versions, suite version, Java rules, semantic judges, prompt/rubric/model aliases, thresholds, budgets, agreement policy, and report schema. Callers MUST NOT override fixed judge internals per run.

#### Scenario: Replay is created

- **WHEN** an authorized caller selects an approved plan and Dataset Version
- **THEN** SRE SHALL persist the complete frozen plan and per-case work before execution

### Requirement: Restartable bounded execution

Replay execution SHALL use bounded concurrency, leases, time, retries, cost, and item counts. Restart recovery SHALL not blindly repeat uncertain external judge calls or infer success.

#### Scenario: Worker stops after judge request

- **WHEN** completion cannot be proven after restart
- **THEN** the attempt SHALL become an explicit uncertain/non-passing state
- **AND** only an authorized bounded retry or new replay may execute again

### Requirement: Deterministic Java Rule Judge

Java Rule Judge SHALL execute fixed versioned deterministic rules within SRE core and persist each independent outcome and finding code.

#### Scenario: Same rule input is replayed

- **WHEN** input digest and rule version are identical
- **THEN** normalized result SHALL be equivalent

### Requirement: Six semantic judges

The required semantic catalog SHALL include RootCauseJudge, FaultSourceJudge, FaultChainJudge, EvidenceGroundingJudge, TaskCompletionJudge, and InvestigationQualityJudge. DeepEval SHALL be stateless; SRE SHALL own attempts, results, aggregation, and history.

#### Scenario: One required judge is unavailable

- **WHEN** five judges return valid results and one times out or returns invalid output
- **THEN** the case and any required aggregate SHALL be INCOMPLETE
- **AND** no passing result SHALL be inferred for the missing judge

### Requirement: Judge-human agreement

Agreement evaluation SHALL compare a fixed judge version with authenticated human labels on the same immutable case versions and rubric. The snapshot SHALL include sample membership, sample count, raw agreement, confusion data, calculation version, and configured chance-corrected metric.

#### Scenario: Sample is below policy minimum

- **WHEN** agreement is calculated with insufficient eligible labels
- **THEN** the judge version SHALL remain ineligible for release-critical use regardless of observed agreement percentage

### Requirement: Authoritative aggregation and report

Reports SHALL preserve per-case and per-judge outcomes, stable failure classes, durations, versions, input/cohort digests, correlations, and replay lineage. Aggregate PASS requires every policy-required result to be valid and passing; known valid failures produce FAIL; uncertainty produces INCOMPLETE.

#### Scenario: Replay is repeated from persisted artifacts

- **WHEN** original interactive/session inputs are unavailable
- **THEN** a distinct linked run SHALL execute from the frozen Dataset Version and artifacts
- **AND** the original run SHALL remain immutable

### Requirement: Safe telemetry and APIs

Replay APIs, SSE, logs, metrics, and audit SHALL be authenticated, bounded, and free of credentials, prompts, evidence bodies, raw model output, and high-cardinality metric labels.

#### Scenario: Capacity is exhausted

- **WHEN** the replay queue or external judge budget is full
- **THEN** readiness/capacity and stable rejection state SHALL be exposed without leaking case content

