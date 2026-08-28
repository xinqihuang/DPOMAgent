# DPOMAgent authority H2 and MySQL contracts

Date: 2026-08-27

The same nine inherited persistence contracts ran against H2 and a dedicated
local MySQL 8 schema named `dpom_authority_contract`:

- insert and unique authority identity;
- one-winner optimistic concurrency;
- rollback of a head update when immutable revision insertion fails;
- exact revision history reconstruction and resume after reload;
- concurrent terminalization with exactly one committed conclusion.
- bounded successful/unavailable ToolUse persistence without raw bodies.
- atomic terminal source and publication-intent commit.
- fail-closed invariant and persistence rollback with no pending intent.

H2 result: PASS; 9 tests, 0 failures, 0 errors.

Real MySQL result: PASS; 9 tests, 0 failures, 0 errors. The test used the
operator-managed forward SQL, left the authority tables empty, and did not use or
modify the existing `dpom_agent` application schema. Credentials are supplied
only through process environment variables and are not recorded in evidence.

Both runs completed with Checkstyle reporting 0 violations.
