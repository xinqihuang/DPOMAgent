# Phase 1/5 Realignment Baseline Inventory

- Captured: 2026-08-27 (Asia/Shanghai)
- Change: `realign-phase1-phase5-to-dpomagent-authority`
- Method: read-only Git branch/HEAD/upstream and `git status --porcelain=v1 --untracked-files=all`
- Safety rule: every path listed in the repository status snapshots is preserved as pre-existing work unless a later task records an intentional overlapping patch. No reset, checkout, clean or destructive rewrite is permitted.

## Repository identities

| Repository | Branch / upstream | HEAD | Ahead / behind | Exact status entries | Status SHA-256 |
| --- | --- | --- | --- | ---: | --- |
| DPOMAgent | `main` / `origin/main` | `ca5c3f82f514e6155ddb21f8e36e76695b9a886c` | 0 / 0 | 119 (20 tracked, 99 untracked files) | `d7f8b064f86b9621c748633d3d4e6632ae8f837dc3bfc15e497e2fabee649d0a` |
| DPOMBaseMCPServer | `master` / `origin/master` | `7f33e684d2224cbfc822e6c1d7ed4ac44190eecc` | 0 / 0 | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| SREIntelligenceService | `main` / `origin/main` | `ce6c38eb31f478f4b71a29be6b7389066887f3e5` | 0 / 0 | 2 tracked | `4c196c99973e8d196e93658b57c07c01c099209551f0b08946f15b4646e380c7` |
| DeepEvalService | `main` / `origin/main` | `8c5f9578a5722df3ad3142642c692f63505d36c5` | 0 / 0 | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| HuaweiCloudAlarmChangeGuard | `main` / `origin/main` | `f2a98d826894b2444f68f9395d15e2fdfb06d0ec` | 0 / 0 | 12 (4 tracked, 8 untracked files) | `96bc9b73d3c926461da23b1d253da8ecd2f597f3e2cd97d1c4c2a4e0b967628d` |

The two SRE paths are the Phase 2 real-model gate correction made immediately before this baseline: `scripts/verify-semantic-real-model.ps1` and `docs/runbooks/semantic-judge.md`. All DPOMAgent and AlarmChangeGuard paths predate this realignment apply session and are treated as user-owned existing work.

## Exact snapshots

- `evidence/baseline/DPOMAgent.status.txt`
- `evidence/baseline/DPOMBaseMCPServer.status.txt`
- `evidence/baseline/SREIntelligenceService.status.txt`
- `evidence/baseline/DeepEvalService.status.txt`
- `evidence/baseline/HuaweiCloudAlarmChangeGuard.status.txt`

Each snapshot includes branch, full commit, upstream, ahead/behind and every tracked or untracked file path. No credential values or file bodies are captured.
