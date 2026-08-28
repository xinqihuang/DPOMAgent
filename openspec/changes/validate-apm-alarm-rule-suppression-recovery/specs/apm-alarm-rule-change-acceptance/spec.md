## Purpose

Define a safe, reversible, and auditable live-acceptance contract for suppressing and restoring one explicitly authorized Huawei Cloud APM alarm rule.

## ADDED Requirements

### Requirement: Published token-authenticated API only
The acceptance run SHALL call only the published Huawei Cloud APM alarm-center endpoints and SHALL authenticate them with a project-scoped `X-Auth-Token` plus every required tenant or business header. It MUST NOT substitute AK/SK request signing for endpoints that accept token authentication only.

#### Scenario: Valid authentication contract
- **GIVEN** a project-scoped token and the target business identifier
- **WHEN** the acceptance client performs the read-only rule precheck
- **THEN** it sends the token and required business header to the published regional APM endpoint without exposing credential values

#### Scenario: Token or required header is unavailable
- **GIVEN** the token or a required tenant or business header is missing
- **WHEN** an acceptance run is requested
- **THEN** the run fails closed before any provider mutation is attempted

### Requirement: Explicitly authorized non-production target
The acceptance run MUST operate only on an explicitly approved, disposable non-production alarm rule whose region, project, business identifier, rule identifier, name, and initial enabled state have been verified from a successful provider read.

#### Scenario: Exact target is verified
- **GIVEN** an approved non-production rule and its expected identity fields
- **WHEN** the provider read returns every expected identity field and shows the rule is enabled
- **THEN** the rule becomes eligible for the guarded suppression/recovery sequence

#### Scenario: Target verification fails
- **GIVEN** a missing, ambiguous, mismatched, unreadable, or initially disabled target rule
- **WHEN** the precheck evaluates the provider response
- **THEN** the run fails closed and sends no update request

### Requirement: Guarded reversible mutation sequence
The system SHALL require a valid HuaweiCloudAlarmChangeGuard approval and deadline before mutation, SHALL disable the exact rule and verify the disabled provider state, and SHALL restore the original enabled state in a `finally` recovery path followed by a final provider readback.

#### Scenario: Full acceptance succeeds
- **GIVEN** an approved exact target and an unexpired mutation authorization
- **WHEN** the disable request succeeds and the disabled readback is observed
- **THEN** the system restores the original enabled state and verifies that final state before reporting acceptance success

#### Scenario: Failure occurs after disable
- **GIVEN** the provider may have accepted the disable request
- **WHEN** readback, evidence capture, or a later acceptance step fails
- **THEN** the system still attempts restoration and records the final provider-observed state

#### Scenario: Process is interrupted with an outstanding mutation
- **GIVEN** an acceptance operation was persisted before the provider mutation
- **WHEN** the service restarts or recovers the incomplete operation
- **THEN** it reconciles provider state and attempts restoration without requiring a new diagnostic workflow to own the mutation

### Requirement: No unverified write
The system MUST NOT send an APM update request unless the immediately preceding read precheck succeeds, the exact target is matched, required permissions include read and update access, and the guard authorization remains valid.

#### Scenario: Provider denies read permission
- **GIVEN** the read precheck returns an authorization error such as `apm2.00000004`
- **WHEN** the acceptance run evaluates the result
- **THEN** it records the blocked outcome and sends no update request

#### Scenario: Authorization expires before update
- **GIVEN** the precheck succeeded but the guard deadline expires before mutation
- **WHEN** the update would otherwise be sent
- **THEN** the system rejects the mutation and reports an expired authorization

### Requirement: Exact and secret-safe acceptance evidence
The acceptance run SHALL retain bounded provider request metadata, response status, response body, provider request identifier, timestamps, guard audit identifiers, and before/disabled/restored state observations. It MUST exclude tokens, passwords, AK/SK values, cookies, and authorization headers from logs, Git, and acceptance artifacts.

#### Scenario: Provider rejects a request
- **GIVEN** a provider request returns a non-success status
- **WHEN** evidence is captured
- **THEN** the bounded response is preserved without paraphrase together with its request identifier and with all credentials removed

#### Scenario: Acceptance succeeds
- **GIVEN** the full disable and restore sequence completes
- **WHEN** the acceptance record is published
- **THEN** it contains independently checkable before, disabled, and restored observations and the corresponding audit linkage

### Requirement: Truthful completion status
The change SHALL be reported complete only after a permitted live run proves disable, provider readback, restoration, and final provider readback for the approved target. Unit tests, mocks, an authentication success without APM authorization, or an unexecuted update MUST NOT be reported as live acceptance success.

#### Scenario: IAM permission remains unavailable
- **GIVEN** a valid project token but insufficient APM alarm-rule privileges
- **WHEN** status is published
- **THEN** the change remains blocked with the exact provider error recorded and the target rule unchanged

#### Scenario: Every live gate passes
- **GIVEN** the provider-observed disable and final restoration are both proven
- **WHEN** all evidence and secret-safety checks pass
- **THEN** the change may be marked complete and archived
