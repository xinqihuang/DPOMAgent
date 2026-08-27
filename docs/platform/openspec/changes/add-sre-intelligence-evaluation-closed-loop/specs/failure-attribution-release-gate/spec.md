# failure-attribution-release-gate Specification

## Purpose

Define evidence-backed failure attribution, capability-gap and improvement recommendations, and fail-closed release governance.

## Requirements

### Requirement: Versioned failure taxonomy

SRE SHALL use a fixed versioned taxonomy that distinguishes data ingestion, evidence availability/integrity, reconstruction, diagnosis reasoning, tool selection/execution, judge infrastructure, model behavior, and policy failures. Observed facts SHALL be stored separately from inferred attribution.

#### Scenario: Evaluation fails because a judge is unavailable

- **WHEN** the required semantic service times out
- **THEN** the failure SHALL be classified as evaluation infrastructure/unavailable
- **AND** it MUST NOT be attributed to diagnosis capability without supporting evidence

### Requirement: Immutable attribution and review

An attribution SHALL reference exact Dataset, Replay Run, case, component, judge, model, prompt/rule, and evidence versions as applicable. Human confirmation or correction SHALL append a new review record rather than rewriting original facts.

#### Scenario: Reviewer changes inferred cause

- **WHEN** an authorized reviewer supplies a supported correction
- **THEN** the correction SHALL supersede the prior inference in current views
- **AND** both records SHALL remain auditable

### Requirement: Capability-gap aggregation

Capability gaps SHALL aggregate only comparable failures and SHALL require minimum samples, data-quality checks, bounded dimensions, and supporting/contradicting case references.

#### Scenario: Only one ambiguous case exists

- **WHEN** the configured sample or confidence requirement is not met
- **THEN** the platform SHALL not publish a confirmed capability gap

### Requirement: Improvement recommendation

Recommendations SHALL be advisory, versioned, and linked to confirmed gaps. Each recommendation SHALL identify a bounded target surface, expected benefit, risk, validation Dataset Version, success/regression criteria, and rollback condition.

#### Scenario: Recommendation lacks validation evidence

- **WHEN** no approved dataset or measurable success criterion is bound
- **THEN** the recommendation SHALL remain DRAFT and MUST NOT enter release governance

### Requirement: Versioned Gate Policy

A Gate Policy SHALL bind the approved baseline, compatible dataset/cohort rule, required suite and judge versions, agreement eligibility, freshness window, minimum samples, regression thresholds, decision schema, and waiver policy. Callers MUST NOT override these controls per decision.

#### Scenario: Candidate and baseline are incomparable

- **WHEN** cohort, dataset, schema, or required component versions violate compatibility policy
- **THEN** the gate SHALL return BLOCK with stable incompatibility reasons

### Requirement: Fail-closed gate decision

ALLOW SHALL require every mandatory evaluation to be complete, fresh, integrity-valid, sufficiently sampled, agreement-eligible, and within threshold. FAIL, INCOMPLETE, missing, stale, unavailable, incompatible, or insufficient evidence SHALL produce BLOCK.

#### Scenario: One required result is missing

- **WHEN** all reported scores pass but a required case or judge result is absent
- **THEN** the decision SHALL be BLOCK

### Requirement: Immutable decision and waiver

Gate decisions SHALL be immutable and reproducible from policy and evidence snapshots. A waiver SHALL be a separate authenticated, time-bounded, reasoned, append-only record and SHALL never convert or overwrite BLOCK as ALLOW.

#### Scenario: Waiver expires

- **WHEN** the waiver validity period ends
- **THEN** consumers SHALL observe the original BLOCK unless a newer valid decision exists

### Requirement: Improvement Agent boundary

The Future Improvement Agent MAY draft recommendations and candidate evaluation requests but MUST NOT approve cases/datasets, alter policies, issue waivers, activate releases, deploy candidates, or execute production remediation.

#### Scenario: Agent proposes its own adoption

- **WHEN** a candidate passes its requested replay
- **THEN** the platform SHALL still require an authenticated human release decision through normal governance

### Requirement: Safe decision visibility

Gate reports, notifications, SSE, logs, metrics, and audit SHALL expose bounded reasons and references without credentials, prompts, raw model output, evidence bodies, or high-cardinality metric labels.

#### Scenario: Gate blocks on sensitive evidence failure

- **WHEN** evidence is rejected for sensitive content
- **THEN** the decision SHALL expose a stable redaction/integrity reason only

