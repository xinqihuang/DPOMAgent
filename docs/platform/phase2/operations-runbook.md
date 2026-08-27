# Phase 2 Operations Runbook

- Scope: curated Incident Cases, Dataset Versions, replay work, six-Judge results, human labels, and agreement snapshots.
- Safety posture: every mutation/worker/API feature is disabled by default; MySQL is authoritative; evidence bodies remain in controlled OBS storage.
- Last exercised: 2026-08-26, local non-production MySQL `3306`, Kafka `9092`, and DeepEval fake adapter `18081`.

## Enable and disable

Enable only the minimum capability required for the operation. Production defaults remain `false` for `sre.phase2.review-mutations-enabled`, `dataset-mutations-enabled`, `dataset-jobs-enabled`, `replay-api-enabled`, `replay-dispatch-enabled`, and `agreement-decisions-enabled`.

1. Confirm `/actuator/health` and Phase 1 Kafka readiness are `UP`.
2. Enable read APIs first, then the specific mutation/job worker.
3. Confirm bounded queue/capacity telemetry before admitting work.
4. Disable admission APIs before workers during a stop. Let current transactions finish, then disable dispatch/jobs.
5. Never enable replay dispatch without a reachable allow-listed DeepEval endpoint and a runtime-injected service token.

Expected safe-disabled evidence is an unavailable mutation endpoint or stable disabled response, with no database writes. Tokens, API keys, prompts, evidence bodies, ground-truth bodies, and raw model output must never appear in configuration files or command history.

## Retention and redaction

- Retain immutable source snapshot metadata, Case Versions, Dataset Versions/membership, Replay Plans/results, labels, agreement snapshots, and lifecycle audits for the governed audit period.
- Evidence bodies remain in an approved OBS retention class. MySQL stores only identity, version, bounded metadata, checksum, media/schema identity, expiry, and integrity state.
- Expiry or deletion of an artifact never rewrites historical rows. Resolution changes to `EXPIRED` or `MISSING`; dependent Silver/replay work fails closed.
- Before export, allow only stable IDs, versions, status, bounded reason codes, digests, counts, and approved component versions. Exclude credentials, arbitrary exceptions, evidence/ground-truth bodies, prompts, and raw provider output.
- Verify redaction with `mvn -q -o clean verify`; the Phase 2 boundary/redaction tests fail if prohibited material enters APIs, logs, metrics, audits, or progress events.

## Capacity controls

- ODS-to-DWD, DWD-to-DWS, and Dataset materialization use bounded item counts and durable watermarks/work rows.
- Replay freezes `maxConcurrency`, attempts per Judge, overall deadline, and cost units in the Replay Plan. A missing, timed-out, invalid, unavailable, or budget-exhausted required Judge is `INCOMPLETE`, never inferred `PASS`.
- Stop admission when pending work reaches the configured capacity. Do not raise limits until MySQL latency, Kafka lag, DeepEval concurrency/queue, and OBS resolution latency are understood.
- Resume by reducing backlog in bounded batches and watching readiness. Capacity rejection is expected evidence; dropping or rewriting work is not.

## Batch recovery

1. Disable new job admission.
2. Inspect the durable materialization/batch work identity, frozen parameters, watermark, state, restart count, and stable error code.
3. Correct the dependency without editing the frozen parameters or staged members.
4. Invoke the existing restart/stop operator action for the same work identity.
5. Verify equal snapshot/parameters reproduce the same content or membership digest and do not duplicate published rows.

The H2/MySQL integration suites inject writer failure and restart. Expected evidence is one durable work stream, incremented restart metadata, identical digest, and all-or-nothing publication.

## Replay recovery

1. Disable new replay creation but leave read/report APIs available.
2. Recover any `PREPARING` run through the authenticated recover endpoint; partial work is rejected rather than guessed.
3. Allow expired leases to be recovered. Recovery closes the old running attempt as `UNAVAILABLE`, clears the lease, and preserves its generation.
4. A new worker claims a new generation and attempt. Old-generation completion must fail optimistic/generation checks.
5. Re-run only work without an authoritative result. Equivalent redelivery returns the existing result.
6. Finalize only after all required Rule/Judge identities have an authoritative result; otherwise the report is `INCOMPLETE` with explicit missing reasons.

The executable rehearsal is `Phase2EndToEndMySqlContractIT`. Success prints only `PHASE2_E2E_CONTRACT_STATUS=EXECUTED judges=7 restart=RECOVERED outcome=PASS`.

## Local non-production rehearsal

Inject secrets through interactive/runtime environment variables; do not place them in the command. Required gates are:

```text
SRE_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:3306/<dedicated-test-db>
SRE_MYSQL_TEST_USERNAME=<runtime-user>
SRE_MYSQL_TEST_PASSWORD=<interactive-secret>
SRE_MYSQL_TEST_ALLOW_MUTATION=true
SRE_KAFKA_TEST_BOOTSTRAP_SERVERS=127.0.0.1:9092
SRE_KAFKA_TEST_ALLOW_MUTATION=true
PHASE2_E2E_CONTRACT=true
```

Run the `mysql-contract` Maven profile against a dedicated disposable schema. The test truncates its scoped tables and Kafka topic, so it must never target production or a shared broker.

## Rollback

Application rollback is feature-first: disable mutation/admission flags, stop workers, preserve MySQL/OBS records, and deploy the previously accepted binary. Do not reverse immutable business history.

Database rollback uses the reviewed Phase 2 rollback SQL only after verification confirms no Phase 2 data must be retained and an operator has an approved backup. The rollback script refuses unsafe states; never bypass its guards. If data exists, keep the schema and roll back the application flags/binary only.

After rollback, verify Phase 1 ingestion still accepts the fixed Kafka contract, Phase 2 APIs/workers are disabled, no new Phase 2 audit rows appear, and historical exact-version queries remain available when the schema is retained.

