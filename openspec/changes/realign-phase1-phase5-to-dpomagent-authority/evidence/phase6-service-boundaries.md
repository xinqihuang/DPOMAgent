# Phase 6 Service Boundary Evidence

Date: 2026-08-27

## DPOMBase evidence-only boundary

- Extended `EvidenceOnlyArchitectureTest` to reject MyBatis, model clients, diagnostic reports, Investigation persistence, Kafka producers and alarm mutation tools/classes.
- `DPOMBaseMCPServer`: offline reactor `mvn verify` passed all ten modules, including Checkstyle and 107 `agentic-mcp` tests.
- No diagnosis, report, orchestration or cloud mutation responsibility was reintroduced.

## HuaweiCloudAlarmChangeGuard mutation boundary

- APM uses the published Huawei Cloud alarm-center APIs through SDK `HcClient` AK/SK signing. The implementation validates a unique ID, exact detail state, update response body `{"ok":"ok"}`, request ID, approval/audit/rollback state machine and fail-closed errors.
- Official authorization items were verified from the rendered Huawei Cloud API reference: `apm::getAdminInfo` for list/detail and `apm::updateAdminInfo` for status update.
- `HuaweiCloudAlarmChangeGuard`: offline `mvn verify` passed 95 tests with zero failures/errors; six explicitly gated live tests were skipped.

## Authorized live check status

- The read-only inventory succeeded against Huawei Cloud `cn-north-9`: AOM 34 rules and CES 448 rules were
  enumerated through the official SDK surfaces without enabling writes.
- The explicitly test-named, initially enabled AOM `a1111` and CES `test` samples completed the full
  precheck -> approval -> disable -> provider readback -> reverse restore -> final readback flow.
  ChangeGuard operation `27d96ca6-0b75-4762-b3c5-7cffc59712d2` ended in `RESTORED`; both final provider
  observations were enabled. Four successful mutations retained upstream request IDs in the local,
  uncommitted bounded evidence artifact. No credential or provider response body is copied here.
- APM rule `8469` was addressed only through the published API and dedicated ChangeGuard provider.
- Endpoint discovery was corrected: the target region remains `cn-north-9`, while the published alarm-center control-plane endpoint is independently deployable and defaults to the verified `apm2.cn-north-4` endpoint.
- Three live attempts all stopped before any mutation: nonexistent derived endpoint, unserved target-region endpoint, then exact detail precheck returning HTTP 403 `apm2.00000004` because the current IAM identity lacks `apm::getAdminInfo`.
- Task 6.3 remains open only for APM. No blind APM mutation was attempted, and no credential was written to
  evidence or Git. CES and AOM were restored to their exact enabled pre-test state.

## Workspace verifier

- Added `scripts/verify-workspace-boundaries.ps1` covering actual Maven dependencies, forbidden source imports, database aggregate ownership, Huawei Cloud credential ownership and alarm mutation API ownership across DPOMAgent, DPOMBaseMCPServer, HuaweiCloudAlarmChangeGuard, SREIntelligenceService and DeepEvalService.
- The verifier passed against the current workspace.
