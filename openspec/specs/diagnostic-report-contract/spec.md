# diagnostic-report-contract Specification

## Purpose
Defines machine-verifiable diagnosis-only and evaluated diagnostic reports whose immutable facts come from DPOMAgent and SRE authorities and whose presentations cannot invent or strengthen claims.

## Requirements

### Requirement: Versioned canonical report profiles
The platform SHALL define one bounded canonical report envelope with diagnosis-only and diagnosis-plus-evaluation profiles. Every report MUST bind report/schema identity, incident/investigation/run identities, target and time scope, observations, hypotheses, conclusion disposition, evidence references, gaps, recommendations, component provenance and source digests.

#### Scenario: Diagnosis-only report is created
- **WHEN** DPOMAgent has an immutable terminal diagnosis source projection
- **THEN** it SHALL create a canonical diagnosis-only report using only persisted authoritative facts and references

#### Scenario: Evaluated report is created
- **WHEN** SRE has an accepted diagnosis source plus complete evaluation lineage
- **THEN** it SHALL create an evaluated profile retaining exact Eval Case, Dataset, Replay, Suite and every individual Judge result identity

### Requirement: Independent completeness and outcome semantics
Report completeness, diagnostic disposition and evaluation outcome SHALL remain independent. Missing provenance or required evidence MUST produce `INCOMPLETE`; unsupported conclusions MUST NOT become `CONFIRMED`; missing, invalid or unavailable required Judge results MUST prevent evaluation `PASS`.

#### Scenario: Confirmed conclusion lacks evidence
- **WHEN** a report source marks a conclusion confirmed without supporting evidence references
- **THEN** contract validation SHALL fail closed with a stable gap code

### Requirement: Immutable revision lineage
Published canonical reports SHALL be immutable. Later evidence, alarm lifecycle, corrected diagnosis facts or evaluation results MUST create a new revision linked to its predecessor with a bounded reason; prior revisions remain queryable.

#### Scenario: Recovery evidence arrives later
- **WHEN** a later observation changes completeness or conclusion strength
- **THEN** the owning report service SHALL append a superseding revision and MUST NOT rewrite the published report

### Requirement: Deterministic projections
Markdown, Portal, HTML and PDF representations SHALL be deterministic projections from the same canonical semantic view. Every artifact MUST retain report digest and template version and MUST NOT synthesize facts, hide gaps or strengthen confidence.

#### Scenario: Multiple renderers project one report
- **WHEN** the same canonical report is rendered in supported formats
- **THEN** normalized semantic snapshots SHALL be equivalent and reference the same canonical digest

### Requirement: Ownership-preserving report assembly
DPOMAgent SHALL own diagnosis-only report authority; SRE Intelligence Service SHALL own evaluated report authority; DeepEval SHALL only return individual Judge results; DPOMBase SHALL only supply evidence references; Portal and workflow services SHALL only render or orchestrate.

#### Scenario: Evaluated report needs diagnosis facts
- **WHEN** SRE assembles an evaluated report
- **THEN** it SHALL consume a versioned immutable DPOMAgent diagnosis source contract and MUST NOT read DPOMAgent or DPOMBase databases

### Requirement: Secure portable contract conformance
Schema, semantic invariants, fixtures, canonicalization vectors and snapshots SHALL be version controlled with the producer and consumable from a clean clone without sibling repositories. Contract and rendered surfaces MUST reject credentials, prompts, raw model output and unrestricted evidence bodies.

#### Scenario: Consumer builds in isolation
- **WHEN** DPOMAgent or SRE is cloned without machine-specific workspace contracts or root governance files
- **THEN** its contract verification and build SHALL succeed from repository-owned or versioned dependency inputs
