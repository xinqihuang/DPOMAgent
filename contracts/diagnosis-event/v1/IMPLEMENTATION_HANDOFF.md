# Diagnosis Event v1 Implementation Handoff

- OpenSpec change: `define-ai-sre-evaluation-boundaries`
- Implemented: 2026-08-21
- Scope: architecture decisions, neutral contract, conformance fixtures, offline validation, follow-up change boundaries
- Runtime/deployment changes: none

## Validation Results

### Strict OpenSpec validation

Command:

```powershell
openspec validate "define-ai-sre-evaluation-boundaries" --strict
```

Result: passed.

### Offline contract validation

Command:

```powershell
python contracts/diagnosis-event/v1/validate_contract.py
```

Result:

```text
Diagnosis Event v1 validation passed: schema=1, positive=2, negative=13, staticChecks=2
```

Coverage:

- 1 Draft 2020-12 JSON Schema self-check;
- 2 positive fixtures: bounded inline diagnosis summary and immutable replay Artifact reference;
- 13 negative cases: missing identity, both/neither payload, malformed timestamp, unsupported major, invalid checksum,
  oversized inline payload, idempotency conflict, broker field, provider SDK type, credential marker, filesystem path and
  general OBS management field;
- 2 static checks: neutral contract property surface and unique architecture/data ownership across the workspace and
  repository-local ADRs.

## Follow-up Entry Point

Use `FOLLOW_UP_CHANGES.md` to create separate OpenSpec changes. Start with the three vertical-slice prerequisites rather than
dataset tiering or Nightly Eval. All Java and Python consumers must run the same positive and negative fixtures.
