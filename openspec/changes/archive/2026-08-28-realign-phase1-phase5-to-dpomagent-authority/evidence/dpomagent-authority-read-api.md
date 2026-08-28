# DPOMAgent authority read API

Date: 2026-08-27

DPOMAgent now exposes bounded read-only authority views for Investigation
progress and the immutable diagnosis source:

- `GET /internal/v1/investigations/{id}/progress` uses a non-negative audit
  sequence cursor and a server-bounded page size of 1..100;
- `GET /internal/v1/investigations/{id}/progress/stream` emits stable audit
  sequence event identifiers and resumes after `Last-Event-ID` without
  unbounded server-side streaming state;
- `GET /internal/v1/investigations/{id}/diagnosis-source` verifies the stored
  immutable document digest, source identity and semantic digest before
  returning a bounded projection.

The API is default-off. Enabling it still requires a dedicated read bearer
token; token comparison is constant-time and requests are rejected before
persistence access when the feature is disabled or authentication fails.
Responses exclude prompts, raw model exchanges and evidence bodies. Safe-text
projection redacts bearer tokens and named credential values.

Focused Spring MVC result: PASS; 6 tests, 0 failures, 0 errors, covering
fail-closed authentication, bounded cursor pagination, SSE replay, immutable
document verification, response redaction and safe handling of digest
tampering.

The same verification run included the 9 H2 persistence contracts. The 9 real
MySQL contracts were then rerun against the dedicated
`dpom_authority_contract` schema and also passed with 0 failures and 0 errors.
No credential is recorded in source or evidence.
