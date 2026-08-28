## 1. Transfer and Prerequisites

- [x] 1.1 Transfer the deferred APM portion of realignment task 6.3 into this change, retaining the passed CES/AOM results and the prior APM 403/no-PUT evidence in the realignment record.
- [ ] 1.2 Obtain least-privilege project-scoped IAM authorization for APM alarm-rule read and update operations (`apm::getAdminInfo` and `apm::updateAdminInfo`, or the provider-confirmed equivalents) without storing account credentials in Git or configuration.
- [ ] 1.3 Select and explicitly approve a disposable, initially enabled non-production APM alarm rule; record its expected region, project, business ID, rule ID, and rule name without credentials.

## 2. Automated Safety Verification

- [x] 2.1 Run the HuaweiCloudAlarmChangeGuard unit and architecture suites and verify that DPOMAgent and DPOMBaseMCPServer contain no APM alarm-rule mutation path.
- [ ] 2.2 Verify tests for project-scoped token headers, required business context, exact-target matching, approval/deadline rejection, bounded response capture, and credential redaction.
- [x] 2.3 Verify ambiguous-write, `finally` restoration, persisted incomplete-operation recovery, and final-provider-state failure tests before enabling the live profile.

## 3. Read-Only Live Preflight

- [ ] 3.1 Acquire a fresh project-scoped IAM token at runtime and issue the exact-rule GET through the published regional APM endpoint with the required headers.
- [ ] 3.2 Confirm that the response exactly matches the approved identity tuple and shows the rule enabled; otherwise record the bounded provider response and request ID and prove that no PUT was sent.
- [ ] 3.3 Run the repository secret scan and confirm that tokens, passwords, AK/SK values, cookies, and authorization headers are absent from logs and artifacts.

## 4. Guarded Disable and Restoration

- [ ] 4.1 Create a persisted HuaweiCloudAlarmChangeGuard operation for the exact rule with explicit approver identity, original state, short deadline, and audit correlation.
- [ ] 4.2 Execute disable and provider readback, proving the approved rule is observed disabled before proceeding.
- [ ] 4.3 Execute restoration from the `finally` recovery path and perform final provider readback, proving the rule is observed in its original enabled state.
- [ ] 4.4 Reconcile any ambiguous or interrupted operation from persisted state and do not close the run until the provider state is known and restored.

## 5. Evidence and Closure

- [ ] 5.1 Retain controlled, bounded before/disabled/restored provider responses, status codes, timestamps, request IDs, and guard audit linkage with all credentials removed.
- [ ] 5.2 Publish a sanitized acceptance summary that distinguishes authentication, authorization, mutation, readback, and restoration results and references the controlled evidence.
- [ ] 5.3 Run strict OpenSpec validation, confirm every task and live gate is complete, and archive this change only when final restoration is provider-verified.
