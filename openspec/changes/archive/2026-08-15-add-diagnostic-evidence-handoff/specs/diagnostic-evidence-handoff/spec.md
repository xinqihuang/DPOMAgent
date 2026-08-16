# Diagnostic evidence handoff

## ADDED Requirements

### Requirement: Production diagnosis without source
The system SHALL diagnose with bounded AOM, CES, APM, LTS and CodeGraph evidence without requiring source code
in the production profile.

#### Scenario: Source is unavailable
- **GIVEN** source access is prohibited in the production zone
- **WHEN** an investigation runs
- **THEN** runtime evidence and bounded CodeGraph SHALL remain usable
- **AND** missing source SHALL be recorded as missingEvidence rather than silently assumed

### Requirement: Deterministic escalation eligibility
The system SHALL calculate escalation eligibility from confidence, contradictions and missing evidence, returning a
deterministic EscalationDecision with eligible, reasons, missingEvidence and confidence. Eligibility MUST NOT trigger
upload, and the upload approval status SHALL be a separate persisted decision.

#### Scenario: Evidence is insufficient
- **WHEN** confidence is below threshold or unresolved required evidence exists
- **THEN** escalation SHALL be eligible with machine-readable reasons and missingEvidence
- **AND** no OBS write SHALL occur

#### Scenario: Decision is deterministic
- **GIVEN** the same escalation inputs
- **WHEN** the decision is computed
- **THEN** eligible, reasons, missingEvidence and confidence SHALL be identical

#### Scenario: Eligibility does not imply upload
- **WHEN** escalation is eligible
- **THEN** upload approval SHALL remain NOT_APPROVED until an explicit separate approval is recorded

### Requirement: Redacted and integrity-protected package
The system MUST create a bounded, versioned package containing runtime evidence, provenance, diagnosis hypotheses,
bounded CodeGraph summary, redaction report, manifest and SHA-256 checksums. It MUST NOT contain source,
credentials or unbounded dumps.

#### Scenario: Package is built
- **WHEN** an eligible investigation is packaged
- **THEN** every payload entry SHALL be redacted, checksummed and listed in the manifest
- **AND** the manifest SHALL be deterministic for identical inputs

#### Scenario: Content allow-list
- **WHEN** package content is assembled
- **THEN** only allow-listed sections (alarm/timeline/topology/logs/metrics/code-context/hypotheses/contradictions/
  degradations) SHALL be accepted
- **AND** unknown sections SHALL be rejected

#### Scenario: Forbidden content rejected
- **WHEN** content contains source markers or credential-like values
- **THEN** packaging SHALL be rejected and no package SHALL be produced

#### Scenario: Bounds enforced
- **WHEN** entries or total bytes exceed configured limits
- **THEN** packaging SHALL be rejected rather than silently truncated

### Requirement: Separately approved OBS upload
The system MUST keep OBS upload disabled by default and MUST NOT treat an in-memory fake store as a real OBS transport
in a formal profile. Upload SHALL require a prior, separately persisted approval bound to a specific packageId; the
upload request SHALL NOT accept an approval flag.

#### Scenario: Approval and upload are separate actions
- **WHEN** a caller wants to upload
- **THEN** an approveUpload action SHALL first persist an APPROVED decision (packageId, investigationId, approverRef,
  reason, approvedAt, expiry)
- **AND** the upload action SHALL only read the persisted APPROVED state

#### Scenario: Upload lacks approval
- **WHEN** upload is requested without a persisted APPROVED decision for that packageId
- **THEN** the request SHALL be rejected and no external write SHALL occur

#### Scenario: Approval is bound to a package
- **WHEN** a new package is built
- **THEN** an approval recorded for a previous package SHALL NOT authorize uploading the new package

#### Scenario: Approval expired or rejected
- **WHEN** the persisted approval is REJECTED or expired
- **THEN** upload SHALL be rejected

#### Scenario: No real OBS adapter
- **WHEN** OBS is enabled in configuration but no real adapter exists
- **THEN** upload SHALL fail closed with OBS_ADAPTER_UNAVAILABLE
- **AND** uploadedAt and objectKey SHALL NOT be written
- **AND** a failure audit event SHALL be recorded

#### Scenario: In-memory store is test-only
- **WHEN** a formal production or development profile starts
- **THEN** an InMemoryEvidenceHandoffStore SHALL NOT be assembled as the OBS transport

#### Scenario: Tests never touch real OBS
- **WHEN** unit tests exercise the handoff adapter
- **THEN** they SHALL use a fake/in-memory adapter wired by test-specific configuration
- **AND** MUST NOT connect to a real OBS endpoint

### Requirement: Development-side integrity verification
The system SHALL verify package schema version, every checksum, size, service, release and commit before exposing
evidence for source-aware analysis, and SHALL fail closed on any mismatch.

#### Scenario: Package is tampered
- **WHEN** a downloaded entry does not match its checksum
- **THEN** the package SHALL be rejected and the failure SHALL be audited

#### Scenario: Unsupported schema
- **WHEN** a package schema version is not supported
- **THEN** the package SHALL be rejected

#### Scenario: Version mismatch
- **WHEN** service, release or commit does not match the expected investigation identity
- **THEN** the package SHALL be rejected

#### Scenario: Recover to investigation input
- **WHEN** a package passes verification
- **THEN** it SHALL be recoverable to the existing EvidenceBundle / investigation input
- **AND** source SHALL be supplied from the development-side accurate snapshot, not from the package

#### Scenario: Concurrent import is idempotent
- **WHEN** two imports of the same package race
- **THEN** the database unique key SHALL arbitrate to a single handoff_import row
- **AND** exactly one import SHALL return alreadyImported=false and the other alreadyImported=true
- **AND** a DuplicateKeyException SHALL NOT be exposed to callers

#### Scenario: Duplicate conflict is reconciled against the existing record
- **WHEN** an import insert hits a duplicate-key conflict
- **THEN** the existing handoff_import record SHALL be re-read and its service/release/commit SHALL be verified
- **AND** only a matching identity SHALL return alreadyImported=true

#### Scenario: Non-unique integrity violation is not idempotent
- **WHEN** an import insert fails with a data-integrity error that is not a duplicate packageId
- **THEN** the import SHALL fail rather than masquerade as idempotent success

#### Scenario: Existing record identity mismatch fails
- **WHEN** the re-read existing record has a different service, release or commit
- **THEN** the import SHALL fail with a version-mismatch error

### Requirement: Profile configuration and assembly boundary
The system SHALL assemble production and development behavior from the same engine through Spring conditional
assembly, not through string checks in business methods, and SHALL fail startup on an unknown mode.

#### Scenario: Production assembly
- **WHEN** the production profile is active
- **THEN** escalation, packaging, approval and upload endpoints SHALL be assembled
- **AND** verify/import endpoints SHALL NOT be assembled

#### Scenario: Development assembly
- **WHEN** the development profile is active
- **THEN** download/verify/import endpoints SHALL be assembled
- **AND** approval/upload/package-build endpoints SHALL NOT be assembled

#### Scenario: Unknown mode fails startup
- **WHEN** the configured mode is not production or development
- **THEN** application startup SHALL fail rather than silently degrade

### Requirement: Audit and low-cardinality observability
The system SHALL record append-only audit events for escalation, package build, approval, rejection, upload,
verification and import, including success and failure, with stable event type, result, error code, identifiers and
timestamp, and without evidence body, credentials or sensitive values.

#### Scenario: Audit trail
- **WHEN** any handoff action occurs
- **THEN** an audit record SHALL be written with eventType, result, errorCode, investigationId/packageId and timestamp

#### Scenario: Upload failure audit
- **WHEN** upload fails after approval
- **THEN** a failure audit event SHALL be recorded
- **AND** the APPROVED audit state SHALL be retained without writing uploadedAt or objectKey

#### Scenario: Low-cardinality labels
- **WHEN** handoff metrics are emitted
- **THEN** labels SHALL reuse the existing whitelist (status/resultType/adapter/errorCode)
- **AND** MUST NOT include investigationId, serviceCode, tenant or log text

#### Scenario: Observability failure is best-effort
- **WHEN** metrics or audit recording fails
- **THEN** the escalation, upload or verification result SHALL remain unchanged

### Requirement: Existing safety boundaries remain
The handoff capability MUST NOT introduce RAG, embeddings, vector storage, arbitrary shell, arbitrary filesystem
access, automatic remediation or production write tools, and MUST reuse existing EvidenceBundle, LogRedactor and the
investigation state machine rather than creating parallel models.

#### Scenario: Handoff is enabled
- **WHEN** the application starts with handoff support
- **THEN** all existing safety boundaries SHALL remain enforced
- **AND** no parallel evidence, redaction or state model SHALL be introduced
