# DPOMAgent authority persistence implementation

Date: 2026-08-27

Task 2.2 adds an operator-managed MySQL 8 schema under
`agent-web/src/main/resources/db/deployment/authority-realignment`. It is outside
`db/migration`; no application Flyway migration references these tables.

The schema separates the optimistic current head from immutable revisions,
append-only audit rows and bounded ToolUse rows. The guarded rollback refuses to
drop the tables whenever any authority row exists. The verification script checks
missing tables, invalid counters/digests, missing head revisions and audit gaps.

`MyBatisInvestigationAuthorityStore` freezes every snapshot with RFC 8785
canonicalization and SHA-256, verifies the digest before reconstruction, updates
the head with an expected-version predicate, and appends revisions/audits/ToolUse
inside Spring transactions. ToolUse persistence contains only argument digests,
bounded provider evidence references and status metadata.

Focused command:

```text
mvn -pl agent-core -am test -Dtest=InvestigationAuthorityTest,AuthorityDomainArchitectureTest,MyBatisInvestigationAuthorityStoreTest -Dsurefire.failIfNoSpecifiedTests=false
```

Result: PASS; 11 tests, 0 failures, 0 errors, Checkstyle 0 violations.
