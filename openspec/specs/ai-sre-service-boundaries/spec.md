# ai-sre-service-boundaries Specification

## Purpose
TBD - created by archiving change realign-phase1-phase5-to-dpomagent-authority. Update Purpose after archive.

## Requirements

### Requirement: Evidence-only DPOMBase enforcement
DPOMBaseMCPServer SHALL expose only bounded evidence collection, discovery, deterministic correlation/packaging and controlled configurable OBS artifact transfer. It MUST NOT contain model clients, ToolUse decisions, diagnosis orchestration/state, Investigation persistence, diagnostic-report authority, Diagnosis Event/Progress publication or general production mutation tools.

#### Scenario: Forbidden responsibility is introduced
- **WHEN** active DPOMBase production code or dependencies introduce a forbidden diagnosis, model, report, Kafka producer, Investigation persistence or alarm-mutation responsibility
- **THEN** architecture verification and the build SHALL fail

### Requirement: Production alarm mutation boundary
HuaweiCloudAlarmChangeGuard SHALL be the only service in this platform scope allowed to disable, enable, mask or otherwise mutate APM, CES or AOM alarm state. DPOMAgent MAY request a guarded change through an explicit approval-bound contract but MUST NOT receive cloud mutation credentials or a general write tool.

#### Scenario: Diagnosis recommends alarm suppression
- **WHEN** DPOMAgent proposes suppressing an alarm or rule
- **THEN** the proposal SHALL remain non-executing until HuaweiCloudAlarmChangeGuard validates its authorization, scope, audit and rollback contract

### Requirement: Version-controlled platform sources
The authoritative cross-phase ADR, phase status, producer contracts and acceptance evidence SHALL live in version-controlled repositories after retirement of the root governance repository. Repository builds MUST NOT depend on untracked parent or sibling paths.

#### Scenario: Workspace is reproduced on another computer
- **WHEN** an operator clones the documented repositories and supplies runtime-only configuration
- **THEN** all architecture/status sources and build-time contracts SHALL be available without copying files manually from a machine-specific workspace root
