# Phase 4 Operations Runbook

- Scope: governed improvement proposals, inactive candidate artifacts, sandbox execution, frozen validation, signed dossiers, human adoption decisions and bounded release handoff.
- Safety posture: every Phase 4 capability is default-off; candidates can never be activated by this service; all governed history is immutable or append-only; production credentials, configuration writes and deployment tools are outside the boundary.

## Enable and disable order

Enable read APIs first. Then enable proposal generation, proposal review, candidate creation, sandbox admission, validation admission, dossier signing and human decision admission in that order. Configure operator, reviewer and approver identities independently. Configure the DeepSeek adapter with an allow-listed model alias and inject its credential only at runtime. Configure signing with an approved key identity and runtime key material before enabling dossier publication.

To stop Phase 4, disable new human decisions and dossier publication first, then validation, sandbox and generation admission. Allow active database transactions to finish, stop workers, and retain read APIs for audit. A disabled or unavailable model, signer, Replay/Judge authority or Release Gate is never interpreted as success.

## Retention and redaction

Retain proposal versions and audits; inactive candidate versions and baselines; sandbox plans, attempts and results; validation plans, work and results; exact Phase 2/3 report, comparison and gate references; dossier manifests, signatures, decisions, supersession and handoff references for the governed audit period. Retention must not rewrite immutable rows.

APIs, logs, audit rows, progress and metrics may contain stable identities, versions, fixed states/reasons, counts, approved aliases and canonical digests. They must exclude credentials, raw prompts, provider responses, unrestricted evidence bodies, signing-key material, arbitrary exception text and stack traces. Metrics use only allow-listed low-cardinality labels. Suspected disclosure requires disabling the affected adapter, rotating the runtime secret/key, preserving the audit trail and reviewing bounded outputs before re-enable.

## Capacity and admission control

Sandbox and validation admission reject new work at their configured pending-work limits. Time, cost, attempt, concurrency and output budgets are frozen in each work plan. Do not raise limits until work age, MySQL contention, external evaluator latency and budget-exhaustion rates have been reviewed. Capacity pressure must result in a stable unavailable/capacity outcome, never a partial or inferred PASS.

## Sandbox recovery

1. Disable new sandbox admission when interruption, policy breach or widespread timeout is detected.
2. Inspect the durable work identity, frozen plan digest, attempt/lease state, restart count, budget counters and stable reason code.
3. Preserve the failed or incomplete attempt. Correct only the external cause; do not change frozen inputs.
4. Restart the same work identity through the guarded restart API. Duplicate delivery must resolve idempotently.
5. Verify exactly one immutable terminal result, deterministic digest, inactive candidate state and absence of network, shell, credential, production-write or deployment capability.

Use the kill switch to stop admission and active sandbox work. A killed attempt remains evidence and cannot be relabelled PASS.

## Validation recovery

Validation binds an exact inactive candidate, baseline, approved Dataset membership and Phase 2/3 policy/schema versions. On interruption or dependency unavailability, disable new validation admission, inspect the frozen plan and authoritative Replay/report/comparison/gate references, restore the dependency, and restart the same work identity. Previously completed authoritative results are reused; one immutable validation result is published.

Missing, stale, incompatible, integrity-invalid, insufficient-sample, unsuccessful, cost-regressed or latency-regressed evidence remains failed/incomplete. Only the existing Phase 3 Release Gate authority may produce ALLOW. Operators must not edit predicates or infer a pass from aggregate scores.

## Signing-key incident

On suspected key exposure, disable dossier publication and handoff immediately, remove the affected key identity from the approved runtime set, rotate key material through the external secret mechanism, and retain all manifests and signatures. Verify existing dossiers with their recorded key identities, record the incident outside immutable dossier content, and publish a new superseding dossier only after exact evidence is revalidated and the new signer is ready. Never overwrite or delete the suspect signature.

## Adoption incident and rollback

Human ACCEPT, REJECT, DEPRECATE and SUPERSEDE decisions require an independent authenticated approver and the current optimistic version. Self-approval and service approval are rejected. An original BLOCK remains visible; only an exact, effective, policy-permitted and unexpired waiver can make a dossier eligible. Revoked, expired or mismatched waivers are ineffective.

If an incorrect handoff is accepted, disable decision and handoff admission, notify the normal release owner, prevent downstream deployment using the bounded handoff reference, append a deprecation or superseding human decision, and preserve the candidate as inactive. Phase 4 itself has no deploy, activate, configuration or waiver endpoint.

Rollback is feature-first: disable Phase 4 admission, stop workers, retain MySQL history and deploy the last accepted binary. Run the reviewed verification SQL before and after. The guarded rollback SQL is allowed only with an approved backup on an empty/unpublished dedicated schema; if governed history exists, retain the schema and roll back application code and flags only. Confirm Phase 1–3 ingestion, replay and Release Gate remain healthy.

## Local non-production rehearsal

Use dedicated schemas on MySQL `127.0.0.1:3306`, local Kafka `127.0.0.1:9092`, and the allow-listed DeepEval fixture `127.0.0.1:18081`. Supply database passwords, model credentials and signing material through an interactive prompt or ephemeral process environment; never commit them or include them in command arguments, logs or reports.

The reproducible evidence chain is:

1. `Phase4GovernanceMySqlContractIT` validates reviewed Phase 4 SQL and the MyBatis boundary on a fresh real-MySQL schema.
2. `Phase2EndToEndMySqlContractIT` validates the authoritative Replay/Judge/report chain over real MySQL, local Kafka and the DeepEval fixture, including interruption recovery.
3. `Phase4GovernedImprovementEndToEndTest` validates the complete governed Phase 4 chain against H2 with actual Phase 2/3 service authorities and deterministic test adapters.
4. Full `mvn verify`, contract/redaction/architecture tests and strict OpenSpec validation close the offline regression gate.

The real-infrastructure and deterministic business-flow evidence is deliberately composite; no test is represented as touching a dependency it does not actually invoke.
