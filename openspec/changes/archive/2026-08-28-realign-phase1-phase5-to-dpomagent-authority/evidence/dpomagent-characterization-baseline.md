# DPOMAgent Pre-Realignment Characterization

- Captured: 2026-08-27
- Source baseline: DPOMAgent `ca5c3f82f514e6155ddb21f8e36e76695b9a886c` plus the exact pre-existing worktree recorded by task 1.1
- Command:

```text
mvn -pl agent-web -am test -Dtest=*Investigation*,*DiagnosisEvent*,*Terminalization*,InvestigationToolExecutorTest -Dsurefire.failIfNoSpecifiedTests=false
```

- Result: PASS; 90 focused tests executed, 0 failures, 0 errors, 1 explicitly gated real Investigation E2E skip; Checkstyle 0 across the nine-module reactor slice.

## Proven current behavior

| Area | Current objective evidence | Baseline result |
| --- | --- | --- |
| Investigation lifecycle | State-machine, coordinator, concurrency, rejection, failure, reconciliation, persistence and API tests | Present; H2-focused behavior passes |
| Observation/hypothesis/conclusion | Coordinator and evidence guards persist/query the existing models; terminalization inserts a conclusion | Present but not yet a complete append-only authority contract |
| Tool execution | `InvestigationToolExecutorTest` covers dispatch across code/runtime tools and failure conversion | Execution present; authoritative ToolUse history is absent |
| Terminal transaction | `InvestigationTerminalizationIntegrationTest` covers terminal status, conclusion and outbox creation in one Spring transaction | Present and passing for current H2 schema |
| Diagnosis Event contract | Model, builder, canonicalization and conformance tests | Present for the current v1/v2 assets |
| HTTP outbox delivery | Conditional assembly, adapter, delivery service, replay endpoint/authentication, persistence and metrics tests | Present, default-off and passing |
| MySQL persistence | Existing external MySQL test is environment gated | Not proven by this focused baseline |
| Kafka delivery | No active Kafka publisher/adapter in DPOMAgent | Missing |
| Bounded progress/SSE | No authoritative progress model or SSE endpoint found in active production sources | Missing |
| Canonical diagnostic report | Only unrelated legacy stacktrace report types exist; no Phase 5 canonical report builder/store/API | Missing |

## Characterization gaps that later tasks must close

1. `Investigation` is currently a compact row model and does not itself prove append-only Run/Step/Observation/Hypothesis/Conclusion reconstruction, optimistic stream versions or deterministic authority identities.
2. Tool execution returns bodies/summaries directly and catches arbitrary exception messages; there is no persisted ToolUse record binding contract version, bounded argument metadata, result status/digest and immutable evidence references.
3. Terminalization creates an HTTP-delivery outbox record but has no Kafka transport, publication lease parity or progress outbox.
4. The current V12 schema is a Flyway application migration. The target requires reviewed deployment/verification/safe-rollback SQL and no application-managed production DDL for the new authority surface.
5. No bounded authenticated progress/SSE or immutable diagnosis-source API exists.
6. No diagnosis-only canonical report contract, builder, immutable revisions, renderers or report persistence exists in DPOMAgent.
7. The one skipped real Investigation E2E and gated external MySQL suites are not counted as acceptance evidence.

This report characterizes the current dirty worktree; it does not relabel any missing behavior as complete.
