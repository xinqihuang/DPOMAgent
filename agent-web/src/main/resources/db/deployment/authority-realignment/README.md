# Investigation authority schema

These scripts are operator-managed deployment assets. They are deliberately outside
`db/migration` and are never executed by application Flyway.

Run them against a dedicated schema in this order:

1. `001_authority_forward.sql`
2. `002_authority_verify.sql` (every query must return zero rows)

Before rollback, stop DPOMAgent writers, take a database backup, and run
`003_authority_rollback_safe.sql`. The rollback refuses to drop any table when
authoritative history exists.

