# T201–T204 — Diagnostic evidence handoff

## Goal

Implement a dual-zone evidence handoff: production performs source-free diagnosis, and only insufficient investigations
can be packaged for explicitly approved OBS transfer to a source-aware development zone.

## Acceptance

- Runtime diagnosis does not require source; only bounded CodeGraph output may cross zones.
- Eligibility and approval are separate persisted/audited decisions; approval binds a specific packageId.
- ZIP paths are fixed, inputs bounded, secrets/source rejected, all entries checksummed.
- OBS is disabled by default and cannot accept caller-provided credentials, endpoints, buckets, paths or object keys.
- Profiles isolate interfaces by Spring conditional assembly; an unknown mode fails startup.
- An in-memory fake store is never assembled as OBS in a formal profile; without a real adapter the handoff fails
  closed with OBS_ADAPTER_UNAVAILABLE.
- Approve/reject are separate actions from upload; upload reads only the persisted APPROVED state.
- Concurrent import of the same package is idempotent via the database unique key.
- All handoff actions write append-only audit (success and failure), best-effort.
- No RAG, arbitrary shell or automatic production execution is introduced.
- `mvn clean verify` and strict OpenSpec validation pass.
