# DPOMAgent terminal diagnosis source atomicity

Date: 2026-08-27

`DiagnosisTerminalCommitService` first validates and canonically builds a
`diagnosis-source/v1` projection solely from terminal Investigation facts. It
then saves the optimistic authority revision, immutable diagnosis source and one
transport-neutral PENDING publication intent in a single Spring transaction.

The source includes conclusion disposition, supporting Observation/evidence
references, alternatives, evidence gaps, exact run component provenance and a
canonical SHA-256. It is explicitly a source projection, not a Markdown/HTML/PDF
or evaluated report.

The shared H2/MySQL contract proves:

- successful commit leaves exactly one immutable source and one pending intent;
- non-terminal invariant failure leaves the authority head unchanged and creates
  neither source nor intent;
- forced immutable-source uniqueness failure rolls back the terminal head update
  and creates no pending intent.

Result: PASS on H2 and real MySQL; 9 tests on each database.
