# Phase 4 — Governed Improvement Agent

- Status: Complete / Accepted (2026-08-26)
- Prerequisite: Phase 3 failure attribution and Release Gate are stable and trusted.
- Goal: introduce a governed agent that proposes improvements and proves them through evaluation before humans decide whether to adopt them.
- Acceptance evidence: `docs/phase4/acceptance-report.md`, `docs/phase4/requirement-evidence-matrix.md`, and `docs/phase4/operations-runbook.md`.

## Intended capabilities

- Consume confirmed capability gaps and approved supporting evidence.
- Draft bounded change proposals for prompts, skills, deterministic rules, tool contracts, evidence collection, and regression tests.
- Create candidate versions without mutating active versions.
- Select an approved validation Dataset Version and declare success, regression, cost, and latency hypotheses.
- Request replay evaluation and compare the candidate against the approved baseline.
- Produce a signed improvement dossier containing rationale, diff/reference, evaluation evidence, risks, and rollback plan.

## Human control boundary

The Improvement Agent is advisory. It cannot:

- deploy or activate a candidate;
- modify production configuration;
- approve Gold cases, datasets, gate policies, waivers, or its own proposal;
- access production credentials or general-purpose production write tools;
- suppress failed or incomplete evaluation evidence.

Adoption requires an authenticated human approval and the normal release process. Rejected and superseded proposals remain auditable.

## Entry criteria

- [x] Failure attribution demonstrates stable reviewer agreement.
- [x] Release Gate has an operational history with acceptable false-block and false-allow review.
- [x] Candidate isolation, sandbox execution, budgets, and kill switches are defined and tested.
- [x] Prompt/skill/rule/tool artifacts have immutable inactive versioning, parent lineage and guarded rollback.
- [x] Architecture, API, contract and redaction tests approve the proposal surface and forbid production execution.

## Success criteria

- [x] The agent produces a bounded proposal from a confirmed capability gap.
- [x] The proposal is evaluated on a frozen dataset without contaminating the baseline.
- [x] Results are reproducible from persisted artifacts and canonical digests.
- [x] Human reviewers can accept or reject with full signed evidence and no hidden side effects.
- [x] Adoption, rejection, deprecation and supersession are appended to immutable improvement history.

## Accepted safety boundary

Phase 4 has no deploy, activate, production-configuration or waiver mutation capability. A candidate remains inactive even after human acceptance; acceptance produces only a bounded handoff reference for the normal release process. Missing, stale, incompatible, unavailable, integrity-invalid or regressed validation evidence fails closed.
