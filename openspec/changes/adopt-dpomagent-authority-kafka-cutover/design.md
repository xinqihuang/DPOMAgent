## Context

See `proposal.md` for motivation. DPOMAgent already contains the LLM adapter, ToolUse loop, Investigation state machine,
MyBatis/Flyway persistence and a transport-neutral transactional outbox delivered through HTTP. SRE Intelligence already
has canonical ingestion semantics and DeepEval integration. The previous workspace plan attempted to rebuild that runtime in
DPOMBaseMCPServer; this design instead preserves one diagnosis authority and changes only the downstream transport.

The current `D:\code` Git root also owns cross-service documents and contracts while each runtime service is an independent
repository. That layout breaks clean clones and creates ambiguous ownership. DPOMAgent becomes the governance and contract
home because it owns the source Diagnosis Event and the end-to-end Agent lifecycle.

## Goals / Non-Goals

**Goals:**

- Keep one authoritative, restartable Investigation Runtime in DPOMAgent.
- Add Kafka without coupling core domain or canonical event construction to a broker API.
- Preserve HTTP compatibility until parity, rollback and backlog gates pass.
- Enforce DPOMBaseMCPServer as a bounded tool-only service.
- Make a clean DPOMAgent clone contain authoritative ADR, docs, OpenSpec, contracts and validation scripts.

**Non-Goals:**

- Moving LLM, ToolUse, RCA or Investigation state into DPOMBaseMCPServer.
- Replacing MySQL, introducing event sourcing, changing Diagnosis Event semantics or adding a second broker.
- Retiring DPOMAgent, automatically executing mitigation, or weakening ChangeGuard approval.
- Keeping `D:\code` as a deployable product, build aggregator or Git repository.

## Decisions

### D1: DPOMAgent remains the source authority

All diagnosis lifecycle writes remain local to DPOMAgent. This avoids dual writers, data migration and two divergent Agent
runtimes. DPOMBaseMCPServer remains independently deployable near cloud APIs and exposes bounded tools only.

Alternative rejected: move the Agent runtime into DPOMBaseMCPServer. That contradicts the tool-only boundary, duplicates
working code and couples production credentials to LLM orchestration.

### D2: Extend the existing delivery port with a Kafka adapter

Canonical event construction, persistence, leasing, retries, integrity checking, replay and audit remain transport-neutral.
HTTP and Kafka are adapters behind the same delivery result taxonomy. Kafka-specific producer records and acknowledgements
must not enter core DTOs or MyBatis domain records except bounded transport diagnostics.

Alternative rejected: create a second Kafka outbox table or regenerate events for Kafka. Either option risks identity drift
and destroys parity with the proven HTTP path.

### D3: At-least-once publication with consumer idempotency

Kafka delivery uses the existing immutable event identity and at-least-once semantics. SRE HTTP and Kafka adapters call the
same ingestion application port, which enforces idempotency and fails closed on identical identity with different canonical
hash. Kafka partition keys use a stable aggregate identity to preserve per-Investigation ordering.

Exactly-once distributed transactions are rejected because MySQL and the local Kafka broker do not share a transaction
coordinator and the canonical consumer policy already handles duplicates.

### D4: Configuration-driven, reversible cutover

Delivery modes are `disabled`, `http`, `kafka` and a bounded validation mode that publishes the same event through both
adapters while recording independent outcomes. Kafka cannot become primary until conformance, ordering, failure recovery,
backlog, metrics and rollback tests pass. HTTP removal is a later explicit task after the compatibility window.

### D5: DPOMBase tool-only boundary is enforced in both repositories

The platform ADR and contract define the boundary. DPOMBase adds dependency/configuration/package architecture tests that
reject LLM clients, Agent runtime, Investigation persistence and diagnosis orchestration. DPOMAgent owns the tool client ports
and converts remote responses into internal evidence DTOs.

### D6: DPOMAgent becomes the governance and contract repository

The outer repository content is merged into `DPOMAgent/ADR.md`, `docs/`, `openspec/`, `contracts/` and `scripts/` without
overwriting existing files. Runtime logs, PID files and machine-local configuration are excluded. The outer Git repository is
removed only after DPOMAgent commits and pushes all migrated content and a fresh clone passes inventory validation.

Consumers MUST NOT compile against `../contracts`. Java consumers use a versioned contract test-resource artifact or a
repository-local pinned snapshot with provenance; non-Java consumers use an equivalent pinned package/snapshot. Publishing
mechanics may evolve, but every clean service clone must build without a sibling workspace repository.

## Risks / Trade-offs

- [Dirty DPOMAgent worktree mixes prior implementation with governance migration] → inventory every pre-existing path,
  preserve it, and split commits by provenance where safe.
- [Dual delivery can duplicate evaluation] → reuse idempotency key and canonical hash; treat equivalent duplicate as success.
- [Kafka acknowledgement after broker commit can time out] → retry the immutable event and rely on consumer idempotency.
- [Contract copies drift] → record source version/digest and verify snapshots against the DPOMAgent canonical contract.
- [Removing the outer Git root loses history] → push DPOMAgent first, retain the former remote as archived history, and remove
  only the local root `.git` after a fresh-clone inventory check.
- [Cross-repository boundary tests cannot run atomically] → keep repository-local guards plus a DPOMAgent orchestration script
  that reports each repository commit and result without making sibling checkout a normal build requirement.

## Migration Plan

1. Mark the old three-service convergence change superseded and establish this change as the accepted plan.
2. Merge ADR/docs/OpenSpec/contracts/scripts into DPOMAgent, exclude runtime artifacts, update paths and validate a clean clone.
3. Publish or pin self-contained contract inputs in each consumer; remove all `../contracts` build assumptions.
4. Characterize the current HTTP outbox and SRE ingestion behavior.
5. Add the Kafka adapter and SRE Kafka ingress behind disabled-by-default configuration.
6. Run HTTP/Kafka parity, retry, ordering, restart, conflict, paging and real local Kafka acceptance tests.
7. Enable Kafka validation mode, then Kafka primary with HTTP rollback available.
8. After the compatibility window and backlog drain, retire only the HTTP adapter.

Rollback before step 8 switches the primary adapter back to HTTP using the same persisted outbox content. Rollback after step
8 requires redeploying the last compatible DPOMAgent version; no Investigation ownership or database migration is involved.
