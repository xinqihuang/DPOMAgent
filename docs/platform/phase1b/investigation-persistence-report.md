# Phase 1B Investigation Persistence Verification

Date: 2026-08-25

## Scope

This report closes OpenSpec tasks 4.1 through 4.5 for DPOMBase durable Investigation state.

## Deployment-managed schema

The `agentic-persistence` module owns reviewed forward, compatibility-verification, and rollback-safe SQL
under `db/deployment/phase1b`. The forward asset creates a versioned schema-state record and 13 bounded,
service-local tables for Investigation aggregates, budgets, runs, steps, observations, hypotheses,
conclusions, checkpoints, progress, publication intents, command/external-call recovery facts, and
append-only audit. The verification query is scoped to the active database. The rollback-safe asset
preserves durable facts and records rollback intent instead of dropping data.

Schema changes are never executed implicitly by application startup.

## Persistence and transaction boundary

- Typed MyBatis mapper interfaces use XML-only production SQL; annotation SQL is rejected by the
  repository boundary scan.
- `MyBatisInvestigationPersistenceAdapter` implements aggregate, transaction, progress,
  publication-intent, and audit ports without exposing database types to the domain.
- Terminalization composes optimistic aggregate and budget updates, optional immutable conclusion,
  monotonic progress, append-only audit, and optional unique publication intent in one local transaction.
- Aggregate version checks reject stale writers, and immutable/unique keys reject replacement facts.
- Runs, steps, observations, hypotheses, checkpoints, command receipts, and external-call recovery state
  have typed XML persistence coverage.

## Runtime admission

Persistence is disabled by default. Enabling it requires an explicit JDBC URL, username, password, driver,
and compatible schema version. Startup validates the deployment-managed schema and fails with a bounded,
credential-free message when configuration or schema readiness is invalid. Spring Boot's generic data
source auto-configuration is excluded because this module owns the opt-in data source lifecycle; the
existing application therefore continues to start when persistence is disabled.

## Verification evidence

- Offline H2/MySQL-mode persistence suite: 7 tests, 0 failures, 0 errors, 0 skipped.
- Gated MySQL contract suite: MySQL 8.0.46, 1 integration test, 0 failures, 0 errors, status `EXECUTED`.
- The gated suite explicitly applied the forward SQL to the isolated `dpom_phase1b_contract` database and
  verified schema compatibility, transaction atomicity and rollback, uniqueness, optimistic races,
  restart recovery, and append-only behavior.
- Full `mvn verify` in `DPOMBaseMCPServer`: PASS across all 13 reactor modules.
- Full Surefire aggregate: 109 suites, 478 tests, 0 failures, 0 errors, 0 skipped.
- Checkstyle: 0 violations in every reactor module.
- Default-off Spring application context test: PASS.
- Phase 1 service-boundary scan: PASS, including the XML-only SQL rule.

Runtime credentials were supplied only through temporary process environment variables. No credential was
written to source, SQL assets, test reports, or this report.
