# Phase 3 — Failure Attribution and Release Governance

- Status: Completed (accepted 2026-08-26)
- Prerequisite: Phase 2 immutable Dataset/replay/Judge lineage and approved-model six-Judge acceptance are complete.
- Goal: turn reproducible evaluation failures into actionable capability gaps and fail-closed release decisions.

## Scope

### Failure Attribution

- Classify failures using a fixed, versioned taxonomy across ingestion, evidence, reconstruction, diagnosis reasoning, tool use, judge infrastructure, and evaluation policy.
- Separate observed failure facts from inferred attribution.
- Preserve case, dataset, replay, component, judge, model, prompt, rule, and evidence lineage.
- Support human confirmation or correction without rewriting the original attribution.

### Capability Gap

- Aggregate comparable failures by bounded dimensions such as service capability, tool, evidence source, fault family, and component version.
- Require minimum sample size and data-quality thresholds.
- Expose supporting cases and counterexamples; do not publish unsupported causal claims.

### Improvement Recommendation

- Produce advisory, versioned recommendations tied to one or more confirmed capability gaps.
- Recommend bounded change targets such as prompt, skill, rule, evidence acquisition, tool contract, or test coverage.
- Include expected benefit, risk, validation dataset, rollback condition, and evidence references.

### Release Gate

- Compare a candidate with an approved baseline on compatible, frozen Dataset Versions.
- Return ALLOW only when every required evaluation is complete, fresh, sufficiently sampled, and within policy.
- Return BLOCK for failure, missing evidence, timeout, stale results, incompatible cohorts, insufficient samples, or integrity mismatch.
- Permit time-bounded, authenticated waivers with reason and append-only audit; a waiver does not rewrite the original decision.

## Exit criteria

- [x] Failure taxonomy and attribution contracts are versioned and reproducible.
- [x] Capability gaps link aggregates back to concrete immutable cases.
- [x] Recommendations include evidence, risk, validation plan, and rollback criteria.
- [x] Candidate-versus-baseline comparison rejects incompatible datasets or versions.
- [x] Release Gate fails closed for every incomplete or unavailable dependency.
- [x] Decisions, waivers, and superseding decisions are immutable and audited.
- [x] Metrics remain low-cardinality and contain no evidence bodies or secrets.

Acceptance evidence: `docs/phase3/requirement-evidence-matrix.md` and `docs/phase3/acceptance-report.md`.

## Not in scope

Automatic application of recommendations or automatic production remediation.
