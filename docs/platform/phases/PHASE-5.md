# Phase 5 — Standardized Diagnostic Reports

- Status: Complete / Accepted (2026-08-28)
- Previous acceptance: 2026-08-26, retained as historical evidence for the superseded DPOMBase-owned diagnosis profile
- Archived change: `openspec/changes/archive/2026-08-28-realign-phase1-phase5-to-dpomagent-authority`
- Prerequisite: Phase 1 authoritative diagnosis lineage and Phase 2–4 versioned evaluation, governance and improvement artifacts are stable enough to project without inventing missing facts.
- Goal: replace hand-authored diagnostic reports with a versioned, machine-verifiable canonical report and deterministic human-readable projections.

Phase 5 implementation and cross-service report acceptance are complete under the corrected ownership. The independent
APM rule disable/readback/restore acceptance is tracked separately and is neither Phase 5 report evidence nor a claimed
successful check.

## Scope

### Canonical Diagnostic Report Contract

- Define one bounded, versioned JSON envelope for diagnosis-only and diagnosis-plus-evaluation report profiles.
- Preserve report, incident, investigation, run, target, time-window, evidence, conclusion, evaluation and component lineage.
- Assign stable identities to observations, hypotheses, conclusions, evidence gaps and recommendations.
- Keep raw facts, inference and advisory actions explicitly distinguishable.

### Completeness and Outcome Semantics

- Keep report completeness, diagnostic disposition and evaluation outcome as independent axes.
- Use `COMPLETE | INCOMPLETE` for report integrity and required-input availability.
- Use `CONFIRMED | HYPOTHESIS | UNDETERMINED` for diagnostic claim strength.
- Use `PASS | FAIL | INCOMPLETE | NOT_REQUIRED` for evaluation outcome.
- Missing evidence, provenance, integrity or required Judge results fails closed and remains visible through stable gap codes.

### Evidence, Judge and Version Lineage

- Reference immutable evidence and controlled artifacts instead of embedding unrestricted logs, traces or model output.
- Retain source capability/API, collection window, target scope, digest and sensitivity metadata.
- Evaluation-backed reports retain Eval Case, Eval Run, Suite and every individual Judge result and version.
- Published reports are immutable; corrections or later lifecycle data create a superseding revision with bounded reasons.
- Replay from the same frozen inputs and component versions reproduces the same canonical semantic digest.

### Standard Presentation Templates

- Treat canonical JSON as authoritative and Markdown, Portal, HTML and PDF as deterministic projections.
- Require conclusion, scope and target identity, timeline, decisive evidence, observations, hypotheses and alternatives,
  confidence, evidence gaps, Judge results when applicable, recommendations, safety boundary, provenance and revision history.
- Record report digest and template version in every rendered artifact.
- Permit localization and provider-specific extensions only when they do not alter mandatory semantics.

### Ownership and Service Boundaries

- DPOMAgent owns diagnosis lifecycle facts and produces the bounded diagnosis-only source projection.
- SRE Intelligence Service owns evaluation facts and assembles the immutable evaluated final-report projection.
- DeepEval Service returns individual Judge results and never generates or stores the final report.
- AgentArts Workflow may schedule generation, notifications and approval triggers but is not the report system of record.
- Portal renders and exports approved projections without synthesizing facts, strengthening confidence or hiding gaps.
- No service reads another service's database directly; all inputs use versioned APIs, events or immutable artifacts.

### Contract and Presentation Verification

- Publish JSON Schema, semantic invariants, positive/negative fixtures and cross-language canonicalization vectors.
- Validate schema, item references, evidence requirements, revision acyclicity, profile-specific Judge completeness and digest integrity.
- Snapshot-test Markdown/Portal/HTML/PDF outputs and compare normalized semantic view models.
- Scan canonical and rendered artifacts, logs and tests for credentials, prompts, raw model output and evidence-body leakage.

## Processing Model

Report generation is a deterministic projection over persisted source contracts. An LLM may help create upstream diagnostic
hypotheses under the Investigation Runtime, but it cannot author canonical report fields outside those persisted facts or
change their status during rendering. Generation is idempotent for the same frozen source identity and digest. Later alarm
lifecycle, evidence or Judge results produce a new linked revision instead of rewriting an existing report.

## Exit Criteria

The checked criteria below form the accepted current decision under the corrected DPOMAgent/SRE ownership.

- [x] `contracts/diagnostic-report/v1` has bounded schema, semantic rules, valid/invalid fixtures and offline validation.
- [x] Diagnosis-only and evaluated profiles preserve complete incident/investigation/run/evidence/component lineage.
- [x] COMPLETE/INCOMPLETE, diagnostic disposition and evaluation outcome cannot be conflated by builders or renderers.
- [x] A confirmed conclusion cannot pass validation without supporting evidence references.
- [x] An evaluated report cannot pass completeness when a required Judge is missing, invalid or unavailable.
- [x] Canonical replay over frozen inputs is digest-identical without the original LLM conversation.
- [x] Later alarm lifecycle or corrected evidence produces an immutable superseding revision.
- [x] Markdown and Portal normalized semantic views are equivalent to canonical JSON; HTML/PDF retain the same meaning.
- [x] Report and projection surfaces pass bounds, authorization, redaction and secret-leakage tests.
- [x] The APM alarm `16557989` is reproduced as a golden non-production fixture with the Eden/CodeCache distinction,
      recovery revision and limitations represented structurally rather than only in prose.
- [x] A cross-service acceptance report maps every Phase 5 requirement to objective repository evidence.

## Current Realignment Gates

- [x] DPOMAgent produces and persists diagnosis-only canonical revisions from immutable Investigation facts.
- [x] SRE consumes the versioned DPOMAgent source without cross-database access and retains exact evaluation lineage.
- [x] DPOMBase remains evidence-only and contains no report authority.
- [x] Producer contracts, fixtures and canonical vectors build from isolated clean clones without workspace sibling paths.
- [x] Real MySQL/Kafka diagnosis-only and evaluated-report flows pass under the corrected ownership.
- [x] Corrected requirement matrix and acceptance report replace the historical decision.

## Not in Scope

- Replacing authoritative Investigation, Incident Case, Dataset, Eval Run or JudgeResult stores.
- Embedding raw production evidence, credentials, prompts or unrestricted model responses in reports.
- Letting Portal, Workflow, renderers or an LLM create authoritative diagnosis/evaluation facts.
- Automatic production remediation or execution of recommendations.
- Visual-brand standardization beyond required semantic hierarchy, status and safety presentation.

## Implementation Change

The implementation-ready proposal, delta specifications, design and task breakdown are stored under:

`../../../openspec/changes/add-phase5-diagnostic-report-standardization`
