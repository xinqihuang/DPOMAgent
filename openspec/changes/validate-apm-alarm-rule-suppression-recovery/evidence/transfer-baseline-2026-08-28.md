# APM Acceptance Transfer Baseline

- Transfer date: 2026-08-28 (Asia/Shanghai)
- Source change: `realign-phase1-phase5-to-dpomagent-authority`, task 6.3
- Destination change: `validate-apm-alarm-rule-suppression-recovery`
- Target previously used for read-only preflight: APM rule `8469`, business `111092`, target region `cn-north-9`
- Published alarm-center endpoint verified during preflight: `apm2.cn-north-4.myhuaweicloud.com`

## Preserved provider outcome

Two project-scoped, token-authenticated read prechecks returned HTTP 403 with the exact bounded body:

```json
{"error_code":"apm2.00000004","error_msg":" has no privilege:has no privilege"}
```

Provider request IDs:

- `768c82aea3ac86e33d546f406611e554`
- `bc404e1c87499c70bfe641fa87f9a642`

No PUT was sent, rule `8469` was not changed, and no credential is retained in this artifact. The earlier CES and AOM guarded live checks completed disable/readback/restore/final-readback and both targets finished in their original enabled state; those passed results remain evidence of the realignment change and are not tasks of this APM-only change.

## Transfer decision

APM suppression/recovery remains unaccepted until this change obtains appropriate APM read/update permission and completes the full provider-observed reversible sequence. This transfer is administrative separation, not a waiver or a PASS.
