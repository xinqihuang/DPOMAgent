# Diagnostic Report Contract v1

`diagnostic-report/v1` is the canonical, bounded report envelope. Canonical JSON is authoritative; Markdown, Portal, HTML and PDF are deterministic projections through template `diagnostic-report-standard@1.0.0`.

## Profiles and independent outcome axes

- `DIAGNOSIS_ONLY`: diagnosis lineage is mandatory; `evaluation.outcome` is `NOT_REQUIRED` and Judge lineage is empty.
- `DIAGNOSIS_EVALUATED`: exact Eval Case, Eval Run and Suite plus the six required Judge kinds are mandatory. Every persisted individual Judge result is retained; multiple results may contribute to the same kind, but `judgeResultId` is unique.
- `completeness`: `COMPLETE | INCOMPLETE` describes integrity and required-input availability.
- conclusion `disposition`: `CONFIRMED | HYPOTHESIS | UNDETERMINED` describes claim strength.
- `evaluation.outcome`: `PASS | FAIL | INCOMPLETE | NOT_REQUIRED` describes evaluation only.

No axis implies another.

## Canonicalization and compatibility

Canonical bytes use RFC 8785 JSON Canonicalization Scheme after removing only the top-level `reportDigest`. Arrays whose order is semantic (`timeline`) retain their declared order. Identity collections (`observations`, `hypotheses`, `conclusions`, `evidenceReferences`, `gapCodes`, `recommendations`, `evaluation.judges`, `provenance`, extension keys) are sorted by stable identity before canonicalization. Duplicate identities are invalid; for `evaluation.judges`, identity means `judgeResultId`, not the normalized Judge kind. UTF-8 SHA-256 lowercase hex is stored in `reportDigest`.

Consumers reject unknown major versions and integrity mismatches with stable reasons. Additive minor fields are allowed only under an approved namespaced extension and may be ignored without semantic reinterpretation. A published report is immutable; a correction creates a higher revision with `supersedesReportId` and bounded `changeReasons`. Revision chains must be acyclic.

## Stable gap codes

- `MISSING_REQUIRED_EVIDENCE`
- `MISSING_REQUIRED_JUDGE`
- `UNAVAILABLE_JUDGE`
- `STALE_SOURCE`
- `INTEGRITY_MISMATCH`
- `UNSUPPORTED_SOURCE_VERSION`
- `MISSING_PROVENANCE`
- `REDACTED_REQUIRED_CONTENT`
- `INCOMPATIBLE_EVALUATION`

`COMPLETE` requires no gap codes. Any required missing, unavailable, stale, incompatible, redacted or integrity-invalid input requires `INCOMPLETE`.

## Extension namespaces and safety

Extension keys match `provider.<provider>.<name>@<major>`; v1 includes `provider.huawei.apm@1`. Extensions cannot replace mandatory fields or change statuses. Reports contain references and bounded normalized observations, never raw logs/traces, credentials, tokens, prompts, HMAC/signing material, model responses or arbitrary exceptions. Redaction is represented by a stable marker and gap code.

`canonical-vectors.json` contains cross-language input/canonical/digest vectors. `fixtures/valid` contains every initial profile/outcome combination and `fixtures/invalid/cases.json` describes structural and semantic negative mutations.
