# Tasks Index

| ID | Task | Depends |
|---|---|---|
| T001 | Maven Java Web Skeleton | - |
| T002 | MySQL Investigation Schema | T001 |
| T003 | LLM Adapter & Tool Contract | T001 |
| T004 | Investigation State Machine | T002,T003 |
| T005 | DPOMCodeGraph Client | T001 |
| T006 | Controlled Code Workspace | T005 |
| T007 | Stacktrace Code Investigation | T004,T006 |
| T008 | DPOMBaseMCP Runtime Evidence | T001 |
| T009 | Symptom-driven Hypothesis Loop | T004,T006,T008 |
| T010 | Diagnostic Script Artifact | T009 |
| T011 | Mitigation Script Artifact | T010 |
| T012 | TOP Case: Device Create Not Persisted | T009,T010 |
| T013 | E2E Regression & Benchmark | T007,T012 |
| T014 | Log Template Mining (Drain3) | T008 |
