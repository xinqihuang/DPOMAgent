# Isolated clean-clone verification

Date: 2026-08-27 (Asia/Shanghai)

Five repositories were cloned with `git clone --local --no-hardlinks` into a new directory outside every
service worktree. Builds therefore consumed committed repository content only; no sibling source tree,
untracked file or previously generated `target` directory could satisfy a test.

| Repository | Exact verified commit | Clean-clone gate | Result |
| --- | --- | --- | --- |
| DPOMAgent | `91f6efd1e66b82126c0ba1beee75f5eb913eca10` | Maven nine-module `verify` | PASS: 536 tests, 0 failures/errors, 50 gated skips |
| DPOMBaseMCPServer | `fd08e6d` | Maven ten-module `verify` | PASS: 399 tests, 0 failures/errors, 1 gated skip |
| SREIntelligenceService | `bf040fc` | Maven four-module `verify` | PASS: 354 tests, 0 failures/errors, 6 gated skips |
| HuaweiCloudAlarmChangeGuard | `2b4d9cb` | Maven `verify` | PASS: 96 tests, 0 failures/errors, 7 explicitly gated live skips |
| DeepEvalService | `fc57486` | frozen `uv` sync, Ruff, mypy and pytest | PASS: 68 tests, 0 failures |

Strict OpenSpec validation also passed from the isolated DPOMAgent clone. DPOMAgent's full Spring Boot
repackage succeeded there, proving the earlier running-process JAR lock was a worktree/runtime condition,
not a source or Maven failure.

The first isolated DeepEvalService run objectively exposed its remaining `../contracts` dependency: three
contract tests could not find the former workspace-root directory. Commit `fc57486` moved the twelve pinned
semantic-Judge contract assets into DeepEvalService and changed the tests to resolve them repository-locally.
A fresh second clone then passed Ruff, mypy and all 68 tests. Its twelve assets are content-equivalent to the
pinned SRE consumer copy.

All repository-owned contract files remain version controlled; no build relies on
`AISREPlatformGovernance`, `D:/code/contracts`, or another service's working tree.
