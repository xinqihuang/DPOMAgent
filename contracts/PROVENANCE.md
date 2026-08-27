# DPOMAgent producer-owned contracts

These contracts are version-controlled with their producer, DPOMAgent. The
version is part of each contract path; consumers must pin a released DPOMAgent
commit or artifact and must not read a parent/sibling workspace directory.

Initial import provenance:

- source repository: `AISREPlatformContracts`
- source commit: `66a23e63d9b9b85b4e887a594318ed8cab4cf7bf`
- imported at: `2026-08-27`
- imported files: `38`
- corrected bundle SHA-256 of the UTF-8 `SHA256SUMS` content, including its
  final newline:
  `540ecd42ceaf5ea155c67731b3f8c2ee087d6ab43715ca43e0b914b9fac2144f`

During import, the v2 Diagnosis Event/Progress producer, Kafka ACL/authority
text and diagnosis-source report provenance were realigned from the superseded
DPOMBase authority to DPOMAgent. Canonical fixture manifests and report digests
were regenerated after that semantic correction. The source commit above is
therefore historical provenance, while `SHA256SUMS` is the current authority.

`SHA256SUMS` is intentionally limited to immutable contract assets and excludes
this provenance note. Any contract change must update its version when it is not
backward compatible, regenerate the checksums, and update the aggregate digest
above in the same review.
