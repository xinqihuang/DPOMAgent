# Historical Phase 1B archive

This directory preserves the 2026-08-25 acceptance evidence for the superseded design that moved Investigation
authority into DPOMBaseMCPServer. It is retained for audit only and MUST NOT be used as the current architecture,
cutover runbook or completion decision.

Current authority and execution sources are:

- `../ADR.md` for the DPOMAgent/DPOMBase/SRE/DeepEval boundary;
- `../phases/PHASE-1.md` for current phase status;
- `../../../openspec/changes/realign-phase1-phase5-to-dpomagent-authority/` for implementation and evidence;
- `../../runbooks/phase1b-kafka-cutover-and-http-rollback.md` for the executable HTTP-to-Kafka runbook.

Historical filenames and bodies remain unchanged where possible so old evidence digests and decisions remain
explainable. Any instruction that moves diagnosis authority to DPOMBaseMCPServer is superseded.
