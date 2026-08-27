# Phase 1B source-authority cutover and rollback runbook

> **SUPERSEDED — DO NOT EXECUTE.** This runbook belongs to the historical DPOMBase-owned design. Use
> `docs/runbooks/phase1b-kafka-cutover-and-http-rollback.md`; Investigation authority stays in DPOMAgent.

This runbook is the only allowed order for moving new Investigation ownership from DPOMAgent to
DPOMBaseMCPServer. Historical DPOMAgent rows stay in its database and remain queryable; never copy, delete,
rewrite, or re-parent them.

## Preconditions

Record immutable component revisions, contract snapshots, MySQL schema versions, producer identity, proposed
authority epoch/cutover timestamp, Kafka topic partition counts and offsets, receipt/projection/audit counts and
aggregate digests. Require green Phase 1A characterization, v1/v2 conformance, HTTP/Kafka parity, replay,
capacity, security, rollback, and service-boundary results. SRE quarantine and DPOMBase publication backlog must
be below capacity. No credential value may enter the evidence file.

## Cutover

1. Stop new DPOMAgent admission. Activate its `phase1b-retired` profile only after the preconditions are signed.
2. Drain every in-flight DPOMAgent investigation, or explicitly close it with a bounded non-success state.
   Record remaining counts; do not infer completion.
3. Keep DPOMAgent historical query endpoints available and leave its event delivery disabled.
4. Configure one new epoch and timestamp in DPOMBase and SRE. The producer/schema matrix must contain only
   DPOMBase Diagnosis Event 2.0 and Progress 1.0 identities.
5. Enable DPOMBase persistence, then publication/progress, then source authority. Its startup guard must reject
   incomplete parity/rollback evidence or a non-drained old authority.
6. Enable SRE authority registry and Kafka intake. Confirm readiness, lag, quarantine depth, receipt/projection
   counts/digests, and broker offsets. Verify legacy v1 remains accepted only inside the declared HTTP window.
7. Observe for the full compatibility window. Retirement requires zero split-authority events, zero duplicate
   Eval Cases, stable lag/capacity, successful immutable replay, and no rollback trigger.

## Objective rollback triggers

Rollback when any source fact cannot be recovered, an active-epoch event is rejected incorrectly, duplicate
projections appear, quarantine/backlog reaches capacity, lag exceeds the agreed bound, readiness stays down, or
snapshot/SSE/Kafka progress sequences diverge.

## Rollback within the compatibility window

1. Disable DPOMBase new admission and publication; preserve all its rows and frozen publication intents.
2. Stop SRE Kafka intake and record final offsets/counts/digests. Do not delete receipts or quarantine rows.
3. Restore the previously recorded SRE authority matrix and DPOMAgent admission configuration.
4. Resume only investigations explicitly known to belong to DPOMAgent. Never move DPOMBase aggregate rows.
5. Re-run parity and reconciliation, record the rollback epoch/status, and keep the Phase 1 status In Progress.

## Compatibility retirement

After the observation criteria pass, set SRE's compatibility end time, keep the HTTP adapter deployed for the
declared window, and require post-window DPOMAgent authority to fail with a stable authority rejection. Retain
historical DPOMAgent queries and all audit records according to their original retention policy.
