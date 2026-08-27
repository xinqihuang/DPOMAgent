# DPOMAgent ToolUse security boundary

Date: 2026-08-27

ToolUse state now retains only the tool and contract versions, canonical argument
digest, sorted argument-name metadata, bounded serialized size, explicit target
scope, correlation identity, outcome, timing and structured immutable evidence
references. Each evidence reference includes stable identity, source capability,
adapter, artifact pointer and SHA-256; it cannot carry an unrestricted provider
body.

The domain rejects credential-like argument names, JSON/raw-envelope target
scopes, arguments above 64 KiB, successful results without evidence, non-success
results without a reason and non-success results that claim evidence. The latter
rule prevents unavailable/failed calls from fabricating missing evidence.

Focused domain/store result: PASS; 12 tests, 0 failures, 0 errors.
H2 and real-MySQL persistence results: PASS; 6 tests on each database.
