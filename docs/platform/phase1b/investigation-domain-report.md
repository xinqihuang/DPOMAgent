# Phase 1B Investigation Domain Verification

Date: 2026-08-25

## Scope

This report closes OpenSpec tasks 3.1 through 3.5 for the DPOMBase Investigation domain.

## Module and dependency boundary

- Added framework-neutral `agentic-diagnosis`.
- Added adapter shells `agentic-persistence` and `agentic-messaging`, each depending inward only on `agentic-diagnosis`.
- `agentic-monitoring` depends inward on `agentic-diagnosis` to implement the bounded evidence port.
- `agentic-mcp` remains the sole Spring Boot executable composition module.
- `scripts/verify-phase1-service-boundaries.ps1` validates the allowed compile-time module edges and executable-module rule.
- `DiagnosisArchitectureTest` rejects Spring, MyBatis, SQL, Kafka, Huawei SDK, SRE, DeepEval, and DPOMAgent dependencies from the domain module.

## Domain coverage

The domain module defines stable, framework-neutral types for Incident, Investigation, Investigation Run,
Investigation Step, Observation, Hypothesis, Conclusion, budget, checkpoint, progress, authority epoch,
external-call state, command receipt, publication intent, and append-only audit.

Policies cover:

- lifecycle transitions and optimistic aggregate versions;
- fail-closed atomic budget consumption;
- canonical-digest command idempotency;
- persisted external-call uncertainty with no blind retry;
- explicit restart/resume outcomes;
- atomic eligible terminalization.

COMPLETED and INCONCLUSIVE terminalizations create immutable publication intents in the same transaction
request as the aggregate, conclusion, progress, and audit facts. FAILED and CANCELLED terminalizations do
not create publication intents.

## Ports and evidence adapter

The domain exposes evidence, persistence, transaction, clock, ID, progress, publication-intent, and audit
ports. `CorrelatedEvidencePortAdapter` reuses the existing bounded DPOMBase correlation service, stores the
bounded branch result behind `BoundedEvidenceArtifactStore`, and returns only provider-neutral references,
digests, sizes, and timestamps to the domain.

## Verification evidence

- `mvn verify` in `DPOMBaseMCPServer`: PASS across all 13 reactor modules.
- Surefire aggregate: 108 suites, 471 tests, 0 failures, 0 errors, 0 skipped.
- `agentic-diagnosis`: 10 tests, including architecture, policy, and terminalization suites.
- `agentic-monitoring`: 157 tests, including the evidence adapter and module architecture suites.
- Checkstyle: 0 violations in every reactor module.
- Phase 1 service-boundary scan: PASS.

No runtime credential was written to source, test assets, or this report.
