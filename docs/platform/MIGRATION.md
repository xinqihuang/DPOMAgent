# Governance migration manifest

Imported on 2026-08-27 from the former outer `AISREPlatformContracts`
repository at commit `66a23e63d9b9b85b4e887a594318ed8cab4cf7bf`.

The migration placed the accepted platform ADR, Phase 1–5 roadmaps,
acceptance/runbook evidence and historical planning panorama under
`docs/platform/`. The active realignment OpenSpec change is stored at
`openspec/changes/realign-phase1-phase5-to-dpomagent-authority/`.

Authority after migration:

- `docs/platform/ADR.md` owns the platform service boundary;
- `docs/platform/phases/` owns phase status and exit criteria;
- repository-root `openspec/` owns active changes and specifications;
- `docs/platform/openspec/` and timestamped acceptance snapshots are historical
  evidence and cannot override the accepted ADR;
- producer-owned cross-service contracts live in repository-root `contracts/`.

The outer repository remains untouched until clean-clone builds and consumer
snapshots are verified and pushed. It is no longer an active build or
governance dependency.
