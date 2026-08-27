# Phase 3 Operations Runbook

- Scope: failure attribution, human review, capability-gap materialization, advisory recommendations, candidate comparison, Release Gate decisions, and waivers.
- Safety posture: all Phase 3 features default off; MySQL history is immutable; core calculations have no cloud, Kafka, Spring, MyBatis, DeepEval, or production-write capability.

## Enable and disable order

Enable `read-api-enabled` first. Then enable only the required admission surface: attribution jobs, attribution review, gap jobs, recommendation mutations, Release Gate, and waiver mutations, in that order. Tokens and human identities are independently configured for operator, attribution reviewer, recommendation approver, and release approver.

To stop, disable new Release Gate and mutation admission first, stop bounded materialization jobs, allow active database transactions to finish, and retain read APIs for audit. Empty or shared tokens never authenticate. A disabled or unreachable dependency is not treated as PASS.

## Retention and redaction

Retain taxonomy versions; attribution fact/inference/review revisions; gap snapshots, ordered members and counterexamples; recommendation versions/audits; comparison plans/results/predicates; gate decisions/supersession; and waiver revisions for the governed audit period. Retention never rewrites an immutable row.

APIs, logs, audits, progress and metrics may expose stable identities, versions, fixed statuses/reason codes, counts, component policy versions and digests. Exclude credentials, prompts, evidence or ground-truth bodies, raw model output, arbitrary exception text and stack traces. Metrics use only fixed outcome, policy, action and state labels.

## Capacity and recovery

Attribution and gap materialization reject admission at their configured pending-work limits. Do not raise a limit until MySQL latency, transaction contention and work age have been reviewed.

For attribution or gap recovery:

1. Disable new admission for that job type.
2. Inspect the durable work identity, frozen parameter digest/JSON, state, prepared count, restart count and stable error code.
3. Correct the dependency without changing frozen parameters or staged membership.
4. Restart the same work identity; never create an equivalent replacement to bypass idempotency.
5. Verify exactly one published immutable snapshot and the same canonical digest. A partial publication is an incident.

Stale human review or lifecycle commands return a stable conflict and append nothing. Re-read the current stream, make a new human decision, and submit with the current expected version.

## Release Gate and waiver incident response

Only an all-PASS predicate set produces `ALLOW`. Incomplete, missing, stale, unavailable, ineligible, incompatible, integrity-invalid, insufficient-sample, unknown or regressed inputs remain `BLOCK`; operators must not infer a pass from aggregate scores.

For an unexpected BLOCK, inspect bounded predicates and exact comparison-plan/report digests. Fix or regenerate the authoritative Phase 2 inputs and append a superseding decision; never edit the original.

A waiver is permitted only for the exact blocked decision/candidate, a fixed policy-permitted reason, an authenticated human release approver and a future expiry. The effective view always shows the original BLOCK. On suspected misuse, revoke the same waiver stream with the current optimistic version, disable waiver mutations, preserve revisions, and audit the approver identity. Expired, revoked, mismatched or unsupported waivers are ineffective.

## Local non-production rehearsal

Use the dedicated MySQL schema on `127.0.0.1:3306`, local Kafka on `127.0.0.1:9092`, and the allow-listed DeepEval fixture on `127.0.0.1:18081`. Supply passwords/tokens interactively or through ephemeral process environment variables; never commit them or put them in command arguments.

The evidence chain is:

1. `Phase2EndToEndMySqlContractIT` verifies Kafka ingestion, MySQL/OBS lineage, DeepEval fixture Judges and interrupted replay recovery.
2. `Phase3GovernanceEndToEndTest` verifies failed replay fixture → confirmed attribution → published gap → advisory recommendation → compatible comparison → Release Gate.
3. `Phase3GovernanceMySqlContractIT` verifies the Phase 3 immutable MyBatis boundary on real MySQL.

## Guarded rollback

Rollback is feature-first: disable gate/waiver/recommendation/review/job admission, stop workers, retain MySQL rows, and deploy the last accepted binary. Run the reviewed Phase 3 verification SQL before and after.

The guarded rollback SQL may be used only for an approved backup and an empty/unpublished dedicated Phase 3 schema. Never bypass its refusal checks. If history exists, retain the schema and roll back only feature flags and application code. After rollback, verify Phase 1/2 ingestion and replay remain healthy, no Phase 3 mutations occur, and exact historical reads remain available when the schema is retained.
