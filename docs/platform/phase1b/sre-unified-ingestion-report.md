# Phase 1B SRE Unified Ingestion Verification

Date: 2026-08-25

## Scope

This report closes OpenSpec tasks 6.1 through 6.5. It refactors HTTP compatibility input and the forthcoming Kafka adapter around one transport-neutral ingestion policy while retaining Phase 1A behavior.

## Authority and transport-neutral policy

- `sre-core` now carries an explicit, bounded `ProducerAuthorityContext`, `SchemaSource`, and safe `TransportMetadata`; it has no Spring, Kafka, JDBC, MyBatis, or SQL dependency.
- Diagnosis Event v1 is retained as canonical v1 content and receives the explicit `legacy-dpomagent` authority projection. It is not rewritten into v2 and no `sourceAuthority` field is injected.
- Diagnosis Event v2 accepts only the DPOMBase authority, a non-empty authority epoch, positive source aggregate version, and immutable publication-intent identity. Invalid authority fails closed.
- Domain outcomes distinguish equivalent duplicates, content/idempotency conflicts, authority conflicts, sequence gaps, permanent rejection, and transient failure without coupling the core to transport acknowledgements.

## HTTP compatibility and durable identity

The existing HMAC HTTP controller still performs the Phase 1A size, media type, authentication, header/body identity, schema, canonical hash, acknowledgement, and security checks, then invokes the same ingestion command used by other transports. Acceptance tests verify its v1 canonical content remains unchanged and its stored authority and transport projection are explicit.

Receipt persistence now stores canonical event identity, digest, schema source, producer and authority identity, authority epoch, source aggregate version, publication intent, receipt/quarantine state, ordering cursor, and bounded transport location metadata. Global event, idempotency, and investigation-sequence uniqueness remain in force, with an additional authority identity constraint. Eval Case uniqueness remains one-per-receipt and one-per-event.

Concurrent transport tests prove HTTP and Kafka representations of the same authoritative record converge on one durable receipt and one Eval Case. Once the original is durable, concurrent equivalent delivery and conflicting content or authority produce deterministic duplicate/conflict outcomes and never overwrite it.

## Production schema ownership

- Production Flyway execution is disabled in `application-production.yml`.
- `db/deployment/phase1b/001_phase1a_baseline.sql` is equivalent to immutable Flyway V1–V4 for a fresh schema.
- `002_phase1b_forward.sql` is equivalent to V5 for an existing Phase 1A schema.
- `003_phase1b_verify.sql` checks required authority/transport columns, indexes, invalid projections, and duplicate Eval Cases.
- `004_phase1b_rollback.sql` removes only Phase 1B additions and requires an empty v2 guard result before execution.
- Historical Flyway V1–V5 remain available only to non-production test profiles for reproducibility.

## Verification evidence

- Full `mvn clean verify`: PASS across all 4 reactor modules.
- Surefire: `sre-core` 30 tests and `sre-web` 109 tests; 0 failures, 0 errors. Four explicitly gated external-model/cross-service tests remained skipped.
- Gated MySQL 8.0 contract: 1 integration test, 0 failures, status `MYSQL_CONTRACT_STATUS=EXECUTED`; it covers Flyway V1–V5, uniqueness, row locking, rollback, restart-safe ingestion, rule results, semantic runs, and evaluation suites.
- Deployment verification SQL against isolated `sre_phase1b_contract`: `SRE_DEPLOYMENT_SQL_VERIFY=PASS`.
- No runtime credential was persisted in source, SQL, test output, or this report.
