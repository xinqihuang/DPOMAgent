# Phase 5 diagnostic report evidence

Date: 2026-08-27

## Canonical contract

- DPOMAgent owns the version-controlled `diagnostic-report/v1` schema, README semantics, valid/invalid fixtures,
  APM alarm 16557989 golden revisions, RFC 8785 vectors and template identity.
- The schema has closed and distinct Observation/Hypothesis and Conclusion shapes. Only Conclusion accepts and
  requires `disposition`; standard JSON Schema validation no longer relies on an incompatible `allOf` composition.
- `DiagnosticReportValidator` fails closed on unsupported versions, prohibited secret/prompt/model content,
  orphan references, unsupported confirmed conclusions, completeness/gap mismatch, missing/unavailable Judges,
  inferred PASS, invalid revision lineage and digest mismatch.
- Contract asset checks retain a pinned per-file manifest and aggregate SHA-256 provenance.

## Diagnosis-only authority projection

- `DiagnosisOnlyReportSourceAdapter` accepts an Investigation identity only and reloads the terminal aggregate plus
  its frozen `diagnosis-source/v1` row from DPOMAgent persistence. It rejects missing, non-terminal and
  aggregate-version-mismatched sources.
- `DiagnosisOnlyReportBuilder` deterministically projects persisted timeline, Observation, Hypothesis, Conclusion,
  evidence references and exact run provenance. Confidence defaults to zero because no persisted confidence fact
  exists. Alternatives are not promoted to recommendations, and no LLM free-text report field is accepted.
- MyBatis stores immutable report revisions separately from an optimistic report head. Request fingerprints bind
  the Investigation, expected revision, sorted change reasons and frozen source digest. Recovery is a new revision
  with `supersedesReportId` and an explicit reason such as `ALARM_LIFECYCLE_RECOVERED`.
- H2 and MySQL schemas enforce unique `(investigation_id, request_idempotency_key)` and
  `(investigation_id, revision_number)` identities, source/head foreign keys and digest columns. Deployment SQL
  includes forward, verification and guarded rollback coverage.

## Verification

- DPOMAgent structural, semantic, canonical vector, negative fixture, golden revision and revision-chain suite:
  6 tests, 0 failures/errors.
- Contract asset manifest: 1 test, 0 failures/errors.
- H2 authority/report persistence contract: 20 tests, 0 failures/errors.
- Real local MySQL 8 authority/report persistence contract: 20 tests, 0 failures/errors. The same suite covers
  idempotent replay, idempotency conflict, optimistic concurrent revision, exact pagination/history, immutable
  predecessor bytes and explicit transaction rollback.
- OpenSpec strict validation: PASS.

## Evaluated report and renderers

- SRE consumes the versioned diagnosis-only report as an inbound immutable document and verifies its report digest;
  it does not read the DPOMAgent database.
- Evaluation lineage pins diagnosis report identity/revision/digest, Eval Case identity/version, Dataset
  identity/version/membership digest, Replay Plan/Run, Suite identity/version, and every Judge result/work input
  digest. Dataset or Judge lineage conflicts fail closed.
- Canonical JSON, Markdown, Portal, HTML and PDF projections originate from the same validated semantic tree.
  Metadata, rendering and evidence-reference endpoints use separate constant-time capability tokens; persistence and
  API record guards reject secret-, prompt-, raw-model- and evidence-body-bearing fields.
- SRE Phase 5 focused contract, persistence, authorization, redaction, renderer, builder, service and lineage suites:
  21 tests, 0 failures/errors.
- APM alarm `16557989` remains a non-production two-revision golden fixture. It distinguishes Par Eden Space and
  Code Cache evidence, records source limitations and links the immutable recovered revision to the alert revision.

Phase 5 tasks 5.1-5.6 are complete. Cross-service clean-clone acceptance remains tracked under section 7.
