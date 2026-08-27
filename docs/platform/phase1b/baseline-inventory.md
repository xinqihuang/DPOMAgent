# Phase 1B pre-migration baseline inventory

- Captured: 2026-08-25 (Asia/Shanghai)
- Change: `complete-phase1-three-service-convergence`
- Purpose: immutable starting evidence for task 1.1; this document does not claim any pre-existing change was produced by this Change.
- Exact file-level inventory: `D:\code\docs\phase1b\baseline-worktree-status.txt`

## Ownership and preservation rule

Every modified or untracked path present in the captured inventory predates Phase 1B apply work and is treated as user-owned existing work. Implementations MUST preserve it, MUST NOT run destructive reset/checkout/clean operations, and MUST use incremental patches when touching an overlapping file. A later Phase 1B change report must distinguish pre-existing paths from paths first added or edited by this Change.

## Repository state

| Area | Git state | Revision | Pre-existing status |
|---|---|---|---|
| `DPOMAgent` | `main`, upstream `origin/main`, ahead 1 | `719bf9188a6ab397ba4c1a5c714009a2309924d5` | 13 modified entries, 97 untracked files |
| `DPOMBaseMCPServer` | `master`, aligned with `origin/master` | `9bc1d44826e5420e54be28096336b831c3cc46b3` | 2 untracked files |
| `SREIntelligenceService` | unborn `main` branch | no commit | 300 untracked files; the entire service is pre-existing user work |
| `DeepEvalService` | no local Git repository | none | 53 files; all are pre-existing |
| `contracts` | no local Git repository | none | 27 files; all are pre-existing |

The counts above come from `git status --short --untracked-files=all` or `rg --files`; directory-collapsed Git status was not used for the exact inventory.

## Toolchain

| Tool | Version | Executable / invocation |
|---|---|---|
| Java | Oracle JDK 21.0.11 | `C:\Program Files\Java\jdk-21.0.11` |
| Maven | 3.9.9 | `D:\tools\apache-maven-3.9.9\bin\mvn.cmd` |
| Python launcher default | CPython 3.14.7 | `C:\Users\blue\AppData\Local\Python\pythoncore-3.14-64\python.exe` |
| DeepEval venv Python | CPython 3.12.11 | `D:\code\DeepEvalService\.venv\Scripts\python.exe` |
| uv | 0.8.13 | `python -m uv` |
| OpenSpec | 1.9.0 | `C:\Users\blue\AppData\Roaming\npm\openspec.ps1` |

Plain `mvn` and `uv` are not on PATH. Verification commands must use the pinned Maven path and `python -m uv`; relying on ambient aliases would make the baseline non-reproducible.

## Initial risk observations

- DPOMAgent's uncommitted surface contains the Phase 1A Diagnosis Event/outbox implementation and overlaps files likely needed for characterization. It must be tested in place, not reconstructed from `HEAD`.
- SREIntelligenceService has no commit history, so the captured file inventory and test evidence are the only local starting provenance until the user commits it.
- DeepEvalService and shared contracts have no Git boundary. Phase 1B reports must list exact added/changed files and content digests for those areas.
- Root `D:\code` is not a Git repository; root ADR/OpenSpec/docs changes require explicit file-level evidence rather than a root Git diff.
