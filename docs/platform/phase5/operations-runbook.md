# Phase 5 Diagnostic Report Operations Runbook

## Safety defaults and rollout

Both DPOMAgent diagnosis-only generation and SRE evaluated-report generation/rendering are disabled by default.
DPOMBaseMCPServer only collects bounded evidence and never enables a report surface. Enable one authoritative surface at
a time with environment-injected credentials, verify readiness reports `READY`, then permit traffic. Metadata reads,
rendered projections and controlled evidence-reference dereferencing use separate credentials. Never place credentials
in configuration files, report JSON, renderer output, logs or audit reasons.

## Capacity and bounds

Canonical documents, strings and collections are bounded by `diagnostic-report.schema.json`. Query pages are limited to 100 entries and use revision cursors. Metrics use only fixed operation and bounded outcome labels; identifiers, free-form reasons and gap details are not metric labels. Treat validation, conflict and incomplete-gap counters as operational signals, not diagnostic authority.

## Retention and artifact access

Canonical JSON, source digests, revision links and publication audit rows are immutable records and follow the owning service's incident/evaluation retention policy. Markdown, Portal view objects, HTML and PDF are reproducible disposable artifacts and may be expired earlier. Evidence endpoints return reference metadata only; evidence bodies remain in their authoritative controlled store.

## Revision and compatibility

Never update a published report in place. A correction, recovery event, changed evidence or new evaluation creates a new revision with `supersedesReportId` and a bounded change reason. Readers reject unsupported major schema versions. Additive extensions must use a registered namespace and cannot replace or weaken mandatory fields. Replay reads persisted canonical bytes and verifies the digest without re-running an LLM or Judge.

## Incident response

- Digest mismatch or unsupported source: disable generation, preserve the request/source digests, and investigate the producing contract. Do not repair canonical JSON manually.
- Duplicate request with different digest: retain the quarantined conflict record, stop retries for that request ID, and issue a new request only after reconciling source lineage.
- Missing/unavailable Judge: publish only an `INCOMPLETE` evaluated report with the stable gap code; never synthesize PASS/FAIL.
- Suspected secret or evidence-body exposure: disable generation and rendering, revoke relevant credentials, restrict artifact access, retain non-sensitive audit identity, and follow the security incident process.
- Renderer failure: disable only rendering/export; canonical JSON remains authoritative and readable through the metadata capability.

## Rollback

Set generation and rendering flags to false first. Existing immutable reports remain readable and Phase 1–4 stores continue unchanged. Execute the reviewed safe rollback verification before any DDL rollback; rollback scripts refuse removal while Phase 5 rows exist. Do not delete reports to make rollback pass—retain or migrate them under an approved retention decision.
