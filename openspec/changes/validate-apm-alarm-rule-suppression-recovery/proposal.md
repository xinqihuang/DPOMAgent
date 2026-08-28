## Why

The APM alarm-rule suppression/recovery live gate cannot currently complete because the authenticated read precheck returns `apm2.00000004 has no privilege`. APM acceptance therefore needs an independent, explicitly authorized and reversible change so the Phase 1/5 realignment can close truthfully without treating an unexecuted provider mutation as passed.

## What Changes

- Move the deferred APM live disable/readback/restore acceptance out of the Phase 1/5 realignment gate and track it independently.
- Define a non-production acceptance flow that verifies the exact alarm rule before mutation, disables it, confirms provider state, restores it in a `finally` path, and confirms the final provider state.
- Require project-scoped IAM token authentication and the published APM alarm-center API headers; AK/SK signing is not an accepted substitute for these endpoints.
- Require HuaweiCloudAlarmChangeGuard approval, deadline, audit, and recovery controls for every live mutation attempt.
- Preserve bounded, credential-free provider responses and request IDs as acceptance evidence, while failing closed when authorization or exact-target verification is unavailable.
- Keep APM mutation ownership outside DPOMAgent and DPOMBaseMCPServer; this change does not add diagnostic, orchestration, or cloud-write responsibility to either service.

## Capabilities

### New Capabilities

- `apm-alarm-rule-change-acceptance`: Authorized, reversible, evidence-preserving acceptance of APM alarm-rule suppression and restoration through HuaweiCloudAlarmChangeGuard.

### Modified Capabilities

None.

## Impact

- **HuaweiCloudAlarmChangeGuard:** live acceptance path and its safety/audit verification; implementation changes are required only if the acceptance gate exposes a defect.
- **Huawei Cloud IAM/APM:** requires a permitted project-scoped identity and a disposable, initially enabled non-production alarm rule.
- **DPOMAgent:** OpenSpec ownership and final acceptance reporting only; it does not execute the provider mutation.
- **DPOMBaseMCPServer:** remains a read-only evidence gateway with no APM suppression/recovery responsibility.
- **Operations:** exact provider responses are retained in controlled test evidence, with secrets excluded from Git and logs.
