## 1. Governance and Repository Consolidation

- [ ] 1.1 Inventory the outer repository and DPOMAgent worktree by tracked/untracked path and hash; record exclusions for Kafka runtime PID/log files, credentials and machine-local configuration.
- [ ] 1.2 Merge the accepted ADR, phase documents, OpenSpec history, canonical contracts and repository-independent scripts into DPOMAgent without overwriting existing artifacts; add a migration manifest with the outer source commit.
- [ ] 1.3 Update DPOMAgent `AGENTS.md`, README and OpenSpec context to declare DPOMAgent as the Investigation/Diagnosis authority, allow Kafka only through the diagnosis-event adapter, and declare DPOMBaseMCPServer tool-only.
- [ ] 1.4 Mark `complete-phase1-three-service-convergence` as superseded in governance documentation and replace every active Phase 1/Phase 5 statement that assigns diagnosis authority to DPOMBaseMCPServer.
- [ ] 1.5 Verify a fresh DPOMAgent clone contains ADR/docs/OpenSpec/contracts/scripts, then archive the former outer remote and remove only the validated local `D:\code\.git` metadata.

## 2. Self-Contained Contract Consumption

- [ ] 2.1 Define a versioned contract resource artifact or pinned-snapshot format containing required JSON schemas, fixtures, provenance commit and SHA-256 inventory.
- [ ] 2.2 Update DPOMAgent conformance tests to load canonical repository resources without workspace-relative paths and cover positive and negative fixtures.
- [ ] 2.3 Create repository-local changes for DPOMBaseMCPServer, SREIntelligenceService and DeepEvalService that remove every `../contracts` assumption and verify each clean clone independently.
- [ ] 2.4 Add a platform validation script that compares consumer snapshot/package digests with the canonical DPOMAgent contract version without requiring sibling repositories for normal builds.

## 3. DPOMAgent Authority Characterization

- [ ] 3.1 Add architecture and persistence tests proving DPOMAgent exclusively owns Incident/Investigation/Run/Step/Observation/Hypothesis/Conclusion and recovers without an original LLM session.
- [ ] 3.2 Characterize atomic terminalization, canonical event identity/hash, leases, retry bounds, replay, audit, paging and optimistic concurrency on H2 and real MySQL 8.
- [ ] 3.3 Remove the Phase1B-retired profile and tests that assume DPOMAgent authority retirement; replace them with HTTP-adapter retirement tests that preserve Investigation records and APIs.
- [ ] 3.4 Create an independently executable `docs/tasks/TNN-dpomagent-authority-characterization.md` with tests-first steps and objective Acceptance evidence.

## 4. Kafka Delivery Adapter

- [ ] 4.1 Add Kafka dependencies only to the DPOMAgent adapter/composition boundary and enforce that core/common/persistence contracts contain no Kafka types.
- [ ] 4.2 Implement a Kafka Diagnosis Event delivery adapter behind the existing delivery port with stable partition key, bounded producer settings and explicit acknowledgement classification.
- [ ] 4.3 Add disabled-by-default delivery modes `disabled`, `http`, `kafka` and bounded dual-validation; reject incomplete broker/topic/timeout/retry configuration at startup.
- [ ] 4.4 Persist independent transport attempt/audit facts needed for dual-validation without changing canonical event identity, content or hash.
- [ ] 4.5 Add offline unit tests plus local Kafka 9092 integration tests for acknowledgement, timeout, duplicate publication, ordering, restart lease recovery, DEAD transition and replay.
- [ ] 4.6 Create an independently executable `docs/tasks/TNN-kafka-diagnosis-event-delivery.md` with tests-first steps and Acceptance evidence.

## 5. SRE Kafka Ingestion

- [ ] 5.1 Create a repository-local SREIntelligenceService OpenSpec change requiring HTTP and Kafka adapters to call the same ingestion application port.
- [ ] 5.2 Implement Kafka deserialization/envelope validation without changing canonical Diagnosis Event validation, idempotency conflict or lineage policies.
- [ ] 5.3 Test equivalent duplicates, conflicting hashes, unknown schema versions, per-aggregate ordering, retry/redelivery and poison-message isolation against local Kafka and real MySQL.
- [ ] 5.4 Verify Kafka ingestion remains disabled by default and that the default offline Maven build opens no broker connection.

## 6. DPOMBase Tool-Only Guard

- [ ] 6.1 Create a repository-local DPOMBaseMCPServer OpenSpec change that freezes its MCP/HTTP tool responsibilities and explicitly forbids LLM, ToolUse decisions, RCA, Investigation state and source Diagnosis Event publication.
- [ ] 6.2 Add dependency, package and configuration architecture tests that fail when model clients, Agent runtime, Investigation persistence or diagnosis orchestration enter DPOMBaseMCPServer.
- [ ] 6.3 Verify existing Huawei Cloud and OBS tools still pass Maven compile and focused real-credential acceptance without exposing credentials or adding diagnosis state.

## 7. Transport Parity and Cutover Acceptance

- [ ] 7.1 Run the same canonical event corpus through HTTP and Kafka and compare identity, content hash, SRE stored facts, Judge scheduling and final evaluation lineage.
- [ ] 7.2 Exercise broker outage, SRE outage, producer acknowledgement timeout, duplicate delivery, process restart, backlog drain and rollback to HTTP; record stable errors and low-cardinality metrics.
- [ ] 7.3 Run a real alarm diagnosis through DPOMAgent tool calls to DPOMBaseMCPServer, publish its immutable event through Kafka, ingest it in SRE and evaluate it through DeepEval.
- [ ] 7.4 Publish an acceptance report containing repository commits, contract version/digests, Kafka/MySQL versions, commands, request/event IDs and PASS/FAIL evidence without credentials or raw sensitive evidence.
- [ ] 7.5 Make Kafka primary only after all gates pass; retain HTTP rollback for the documented compatibility window and retire only the HTTP adapter after explicit approval and zero backlog.
