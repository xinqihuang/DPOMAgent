## Context

See [proposal.md](proposal.md) for motivation. The Phase 1/5 realignment has already proven the CES and AOM live disable/restore paths, while APM remains unexecuted because a valid project-scoped token receives HTTP 403 with `apm2.00000004 has no privilege` during the read-only precheck. No update request was sent and the target rule remained unchanged.

HuaweiCloudAlarmChangeGuard is the sole owner of live cloud alarm mutations. DPOMAgent owns investigation and change-governance records but does not call provider mutation APIs; DPOMBaseMCPServer remains a read-only evidence gateway. The APM alarm-center status API uses token authentication and provider-specific business context rather than the AK/SK signing path used by other Huawei Cloud APIs.

## Goals / Non-Goals

**Goals:**

- Make the APM live gate executable once IAM permission and a disposable rule are available.
- Prove provider-observed disable and restoration without risking an unrecovered disabled rule.
- Preserve exact, bounded, secret-safe evidence that distinguishes authentication, authorization, mutation, and readback outcomes.
- Keep the work independent from Phase 1/5 finalization and from diagnostic runtime ownership.

**Non-Goals:**

- Bypassing Huawei Cloud authorization or discovering credentials through automation.
- Mutating a production rule or using an already-disabled rule as the acceptance target.
- Adding APM write tools to DPOMBaseMCPServer or DPOMAgent.
- Treating mocks, unit tests, or IAM token issuance as a substitute for live provider-state verification.
- Refactoring working implementation unless the live gate exposes a concrete defect.

## Decisions

### 1. Track APM acceptance as a standalone change

The incomplete APM portion of realignment task 6.3 is transferred to this change, while the already-passed CES and AOM evidence remains in realignment. This keeps the Phase 1/5 closure truthful and prevents an external IAM dependency from obscuring completed architectural work.

**Alternative considered:** keep realignment active until APM access is granted. Rejected because it conflates a provider-account prerequisite with the completed ownership and Kafka realignment.

### 2. Use a strict preflight identity tuple

The operator supplies the expected region, project, business ID, rule ID, rule name, and initial enabled state. A provider GET must return an exact match before the operation becomes mutable. The acceptance runner does not search broadly and then select a candidate.

**Alternative considered:** accept a rule ID alone. Rejected because a stale ID, wrong business context, or wrong regional endpoint could target an unintended rule.

### 3. Use the published token-authenticated APM contract

The runner obtains a project-scoped IAM token outside persisted configuration and sends `X-Auth-Token` plus the required business header to the published APM alarm-center endpoint. Tokens and user passwords remain runtime-only and are never stored in repository files, evidence bodies, or logs.

**Alternative considered:** add AK/SK signing to the status endpoint. Rejected because the endpoint's authentication contract is token-only and the prior authorization failure must be solved through IAM permission, not an unsupported signing variant.

### 4. Model acceptance as a recoverable guarded state machine

Before any provider write, HuaweiCloudAlarmChangeGuard persists the exact target, original state, approval identity, deadline, and operation state. The normal sequence is `PRECHECKED -> DISABLE_SENT -> DISABLED_CONFIRMED -> RESTORE_SENT -> RESTORED_CONFIRMED`. A `finally` path attempts restoration whenever disable may have reached the provider, and restart recovery reconciles all incomplete operations against provider state.

**Alternative considered:** execute a linear script and restore only after a successful disabled readback. Rejected because transport ambiguity or process interruption can leave the provider changed even when the client did not observe success.

### 5. Separate controlled raw evidence from committed summaries

Bounded provider bodies, status codes, request IDs, timestamps, and before/after observations are retained in a controlled acceptance artifact with credentials removed. Git contains only the durable acceptance summary and non-secret references needed for audit. Secret-pattern and authorization-header checks run before publication.

**Alternative considered:** paste all raw HTTP traffic into the change directory. Rejected because request captures can contain reusable credentials or sensitive tenant context.

### 6. Define completion by final provider state

Success requires an observed enabled rule before the operation, an observed disabled rule after suppression, and an observed restored enabled rule after recovery. If restoration cannot be proven, the run is a recovery incident rather than a passed acceptance, regardless of intermediate API responses.

**Alternative considered:** accept HTTP 2xx from both update calls. Rejected because transport success does not prove provider state or correct target selection.

## Risks / Trade-offs

- **[IAM permission remains unavailable]** → Record the exact 403 response and request ID, send no PUT, and keep this change active without blocking Phase 1/5 closure.
- **[Disable succeeds but the client loses the response]** → Treat every ambiguous write as possibly applied, reconcile by GET, and drive restoration from the persisted original state.
- **[Target rule fires during the short acceptance window]** → Use a disposable non-production rule, a short approval deadline, and immediate restoration after disabled readback.
- **[Provider response contains sensitive data]** → Bound response capture, redact credential-bearing headers and values, store raw evidence only in the controlled artifact, and commit only a sanitized summary.
- **[API behavior changes]** → Fail closed on schema or state mismatches and update the adapter only from published provider documentation and captured evidence.

## Migration Plan

1. Transfer only the deferred APM portion of realignment task 6.3 into this change; retain CES/AOM pass evidence in the archived realignment record.
2. Obtain least-privilege APM read/update authorization for a project-scoped test identity and identify a disposable, initially enabled non-production rule.
3. Re-run automated guard, adapter, recovery, and secret-safety tests before the live operation.
4. Perform read-only preflight. Stop without PUT on any permission, identity, schema, or state mismatch.
5. Execute the approved disable/readback/restore/final-readback sequence and retain controlled evidence.
6. If restoration is not confirmed, stop acceptance closure and operate the recovery path until the provider state is known and safe.
7. Publish the sanitized result and archive this change only after every live gate passes.

Rollback is the restoration half of the same guarded operation. There is no rollout to DPOMAgent or DPOMBaseMCPServer to reverse.
