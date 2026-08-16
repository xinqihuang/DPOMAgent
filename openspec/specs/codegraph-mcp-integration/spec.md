# codegraph-mcp-integration Specification

## Purpose
Defines how DPOMAgent integrates the CodeGraph (colbymchenry/codegraph) stdio MCP server as a controlled,
development-only navigation capability, without leaking its DTOs into core.

## Requirements

### Requirement: Stdio MCP transport via Java MCP SDK
The system SHALL connect to CodeGraph through the Java MCP SDK stdio transport (`codegraph serve --mcp`) and MUST NOT
implement JSON-RPC framing itself.

#### Scenario: Stdio transport
- **WHEN** the development profile starts CodeGraph
- **THEN** the client SHALL use the Java MCP SDK stdio transport over stdin/stdout
- **AND** MUST NOT use the legacy MCP-over-SSE endpoint or self-written JSON-RPC framing

### Requirement: Controlled CodeGraph process
The CodeGraph process SHALL be launched from a server-side configured executable path with arguments fixed by code
(`serve --mcp`). It MUST NOT be invoked through cmd.exe, powershell, sh or bash, MUST NOT accept caller-supplied command,
arguments or environment, and MUST NOT expose an arbitrary shell tool or LLM-invoked start/index/sync commands.

#### Scenario: Fixed invocation
- **WHEN** CodeGraph starts
- **THEN** the executable path SHALL come from server configuration and the argument list SHALL be fixed by code
- **AND** no shell process SHALL be used to spawn it

#### Scenario: No injection surface
- **WHEN** a caller attempts to supply a command, argument or environment variable
- **THEN** the request SHALL be ignored because the process contract exposes no such input

### Requirement: Explicit CodeGraph tool surface
The system SHALL enable `codegraph_explore` as the default tool and SHALL explicitly enable the deterministic DTO tools
`status,node,search,callers,callees,impact,files` through the `CODEGRAPH_MCP_TOOLS` environment variable.

#### Scenario: Tool list
- **WHEN** the CodeGraph MCP client initializes
- **THEN** the enabled tool set SHALL be exactly the configured `CODEGRAPH_MCP_TOOLS` collection
- **AND** `codegraph_explore` SHALL be present

### Requirement: Repository Registry mapping
The system SHALL map serviceCode plus release/commit to a snapshot root/projectPath through a Repository Registry and
MUST NOT fall back to selecting the first repository. An unknown service SHALL fail closed and a commit mismatch SHALL fail
closed.

#### Scenario: Exact match
- **WHEN** a snapshot is resolved for a known serviceCode and commit
- **THEN** the registry SHALL return that exact repository's snapshot root

#### Scenario: Unknown service
- **WHEN** the serviceCode is not registered
- **THEN** resolution SHALL fail closed rather than selecting any repository

#### Scenario: Commit mismatch
- **WHEN** the requested commit does not match the registered commit
- **THEN** resolution SHALL fail closed with a version-mismatch error

### Requirement: projectPath containment
The projectPath passed to CodeGraph SHALL be resolved from the Repository Registry snapshot root and validated with a
real-path containment check; escape attempts SHALL be rejected.

#### Scenario: Contained path
- **WHEN** projectPath resolves inside the snapshot root after real-path normalization
- **THEN** the query SHALL proceed

#### Scenario: Escape or symlink
- **WHEN** projectPath escapes the snapshot root or resolves through a symlink outside it
- **THEN** the query SHALL be rejected

#### Scenario: Query re-validates snapshotId
- **WHEN** a navigation query is issued with a snapshotId
- **THEN** the snapshotId SHALL be re-resolved through the Repository Registry before entering the MCP projectPath
- **AND** an unregistered or out-of-base snapshotId SHALL be rejected

### Requirement: Development-only assembly
The active CodeGraph stdio adapter SHALL only be assembled in the development profile. The production profile MUST NOT
start a CodeGraph process, access source, or create a stdio subprocess. A fail-closed disabled port bean MAY remain to
satisfy the core CodeGraphClient dependency, but it SHALL NOT be a CodeGraph adapter and SHALL NOT spawn processes or
read source.

#### Scenario: Development assembly
- **WHEN** the development profile is active
- **THEN** the CodeGraph stdio client SHALL be assembled and validated against the pinned version

#### Scenario: Production has no active CodeGraph adapter
- **WHEN** the production profile is active
- **THEN** no active CodeGraph stdio adapter or process SHALL exist
- **AND** only a fail-closed disabled port bean SHALL remain, which SHALL NOT spawn processes or access source

### Requirement: Telemetry and update check disabled
CodeGraph telemetry and update checks SHALL be disabled with `CODEGRAPH_TELEMETRY=0`, `DO_NOT_TRACK=1` and
`CODEGRAPH_NO_UPDATE_CHECK=1`; runtime update checks, auto-upgrades and auto-downloads MUST NOT run.

#### Scenario: Disabled by fixed environment
- **WHEN** the CodeGraph process is launched
- **THEN** its environment SHALL include the telemetry and update-check opt-out variables
- **AND** the values SHALL be fixed by code, not caller input

### Requirement: Pinned and verified version
The system SHALL pin a validated CodeGraph version and MUST NOT download it from the public network during build or
startup; formal deployment SHALL use an offline package with a recorded checksum.

#### Scenario: Version recorded
- **WHEN** the adapter is configured
- **THEN** the expected CodeGraph version SHALL be recorded in configuration
- **AND** a version mismatch with the installed executable SHALL fail

#### Scenario: No runtime download
- **WHEN** the application builds or starts
- **THEN** it MUST NOT download CodeGraph from the public network

### Requirement: Internal DTO isolation
CodeGraph MCP DTOs SHALL NOT leak into core; the internal CodeGraphClient port and Symbol/CallStep/ClassHierarchy/
CodeSnapshot DTOs SHALL remain the only contract consumed by core.

#### Scenario: Port boundary
- **WHEN** core consumes code-graph results
- **THEN** it SHALL only see the internal DTOs through the CodeGraphClient port

### Requirement: Tool mapping to internal DTOs
The system SHALL map findSymbol to `codegraph_search`/`codegraph_node`, findCallers to `codegraph_callers`, findCallees to
`codegraph_callees`, findCallChain to structured call paths from `codegraph_explore`, findClassHierarchy to
`codegraph_explore`/`codegraph_node`, and impact to a bounded internal-port summary, degrading safely with a recorded
reason when a result cannot be reliably parsed.

#### Scenario: Structured mapping
- **WHEN** a navigation query runs
- **THEN** the corresponding CodeGraph tool SHALL be called and its result parsed into the internal DTO

#### Scenario: Safe degradation
- **WHEN** a call-chain or hierarchy result cannot be reliably parsed
- **THEN** the client SHALL return an empty or bounded result and record the degradation reason

### Requirement: Versioned parser with fail-closed behavior
Because the official CodeGraph MCP returns text, the system SHALL wrap a versioned parser, backed by fixture contract
tests, that fails closed on unknown or malformed output.

#### Scenario: Known format
- **WHEN** a CodeGraph text result matches the recorded format version
- **THEN** it SHALL be parsed into internal DTOs

#### Scenario: Unknown or malformed format
- **WHEN** a CodeGraph text result does not match the expected format
- **THEN** parsing SHALL fail closed rather than return fabricated results

### Requirement: Navigation with source-of-truth provenance
The graph SHALL be used only for navigation; final source facts SHALL come from the accurate commit snapshot. Evidence
SHALL record provider=codegraph, the pinned version, a safe projectPath identifier, the commit and any degradation reason.

#### Scenario: Evidence provenance
- **WHEN** code-graph evidence is recorded
- **THEN** it SHALL carry provider=codegraph, version, a safe projectPath identifier, commit and degradation reason
