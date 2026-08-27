# DPOMAgent producer-owned contracts

These contracts are version-controlled with their producer, DPOMAgent. The
version is part of each contract path; consumers must pin a released DPOMAgent
commit or artifact and must not read a parent/sibling workspace directory.

Initial import provenance:

- source repository: `AISREPlatformContracts`
- source commit: `66a23e63d9b9b85b4e887a594318ed8cab4cf7bf`
- imported at: `2026-08-27`
- imported files: `39`
- corrected bundle SHA-256 of the UTF-8 `SHA256SUMS` content, including its
  final newline:
  `122b8aae4191ceb8fdea6c60778829a4accb6c52d9a24a04e864f99e2d840d72`

During import, the v2 Diagnosis Event/Progress producer, Kafka ACL/authority
text and diagnosis-source report provenance were realigned from the superseded
DPOMBase authority to DPOMAgent. Canonical fixture manifests and report digests
were regenerated after that semantic correction. The source commit above is
therefore historical provenance, while `SHA256SUMS` is the current authority.

Diagnosis Progress `1.1` additionally preserves DPOMAgent's real admission
facts: an ADMISSION record may precede Run creation and retains aggregate
version zero. Other stages still require a real persisted Run identity.

The diagnostic-report v1 schema was corrected without changing valid instance
semantics: conclusions now have an explicit closed shape, while observations
and hypotheses reject the conclusion-only `disposition` field.

Evaluated-report lineage now pins the versioned diagnosis source, Eval Case,
Dataset, Replay Plan/Run, Suite and every individual Judge result.

`SHA256SUMS` is intentionally limited to immutable contract assets and excludes
this provenance note. Any contract change must update its version when it is not
backward compatible, regenerate the checksums, and update the aggregate digest
above in the same review.
