## Purpose

定义 AI For SRE 各部署单元的权威数据、智能推理、工具、评价和受控写操作边界，防止诊断状态与 LLM 编排在服务之间重复建设或错误迁移。

## ADDED Requirements

### Requirement: DPOMAgent owns diagnosis and investigation
DPOMAgent SHALL be the sole authoritative owner of online Incident, Investigation, Run, Step, Observation, Hypothesis and
Conclusion state. It SHALL own LLM provider isolation, ToolUse decisions, diagnosis orchestration, checkpoint recovery and
diagnosis-event source publication.

#### Scenario: A diagnosis is started
- **GIVEN** Portal submits a valid diagnosis request
- **WHEN** the request is accepted
- **THEN** DPOMAgent SHALL create and persist the authoritative Investigation state
- **AND** no other service SHALL create a competing authoritative Investigation

### Requirement: DPOMBaseMCPServer remains a bounded tool service
DPOMBaseMCPServer SHALL expose versioned, bounded Huawei Cloud evidence, CMDB and controlled Artifact tools. It MUST NOT
host an LLM provider, decide ToolUse, perform RCA, own Investigation lifecycle state, publish source Diagnosis Events or
orchestrate diagnosis business processes.

#### Scenario: DPOMBase dependencies and endpoints are inspected
- **GIVEN** a production DPOMBaseMCPServer build
- **WHEN** architecture tests inspect its dependencies, configuration and public capabilities
- **THEN** the build SHALL expose only bounded tool and evidence responsibilities
- **AND** it MUST NOT contain LLM credentials, Agent runtime or authoritative diagnosis state

### Requirement: Evaluation services remain downstream
SRE Intelligence Service SHALL own evaluation ingestion, lineage, replay, deterministic Judges, aggregation and evaluation
metadata. DeepEval Service SHALL remain a stateless semantic Judge. Neither service SHALL become the source of online
Investigation state or read another service database.

#### Scenario: A Diagnosis Event is evaluated
- **GIVEN** SRE Intelligence accepts a canonical Diagnosis Event
- **WHEN** evaluation and semantic judging run
- **THEN** SRE Intelligence SHALL persist evaluation facts and Judge lineage
- **AND** DeepEval SHALL return bounded Judge results without owning the Diagnosis lifecycle

### Requirement: Production changes use the isolated guard
Production mutation requests SHALL cross HuaweiCloudAlarmChangeGuard with explicit policy and approval semantics. An LLM
MUST NOT receive a generic production-write tool or directly invoke unrestricted cloud mutation APIs.

#### Scenario: A diagnosis recommends changing an alarm rule
- **GIVEN** DPOMAgent produces a mitigation recommendation
- **WHEN** a production rule change is requested
- **THEN** the request SHALL be evaluated by HuaweiCloudAlarmChangeGuard
- **AND** DPOMBaseMCPServer MUST NOT perform the mutation as a general MCP tool

