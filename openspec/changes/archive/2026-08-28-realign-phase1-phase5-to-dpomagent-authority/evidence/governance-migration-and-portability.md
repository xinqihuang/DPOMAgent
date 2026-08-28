# Governance migration and portability gate

Date: 2026-08-27

The accepted platform ADR, Phase 1–5 roadmaps, acceptance/runbook materials and
historical planning panorama now live under DPOMAgent `docs/platform/`. The
active realignment change and evidence live in DPOMAgent root `openspec/`.
`docs/platform/MIGRATION.md` records the former outer repository commit and
defines which migrated paths are authoritative versus historical.

DPOMAgent `AGENTS.md`, README and platform index now consistently state that
DPOMAgent is the Investigation/Diagnosis authority, Phase 1B migrates only its
HTTP outbox transport to Kafka, and DPOMBase remains an evidence-only tool
service.

`scripts/verify-portability.ps1` scans active Maven/Python/PowerShell build
inputs, producer contract documentation and authoritative platform documents.
It rejects machine-specific workspace paths and unversioned parent/sibling
contract references.

Results:

- current DPOMAgent repository: `PORTABILITY_CHECK_PASSED`;
- isolated negative fixture containing a machine-specific contract path:
  `PORTABILITY_NEGATIVE_FIXTURE_REJECTED`.
