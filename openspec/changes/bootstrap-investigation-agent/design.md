# Design: DPOMAgent V1

## 1. Architecture
```text
SRE/Web API
    |
    v
DPOMAgent (single Java Web)
    |
    +-- InvestigationCoordinator
    |     +-- HypothesisPlanner
    |     +-- EvidencePlanner
    |     +-- Observation/Conclusion
    |     +-- ScriptArtifactService
    |
    +-- LLM Adapter ----------> Model Provider
    +-- Runtime Adapter ------> DPOMBaseMCPServer
    +-- CodeGraph Adapter ----> DPOMCodeGraphService
    |                              |
    |                          Snapshot/CGC
    |                              |
    +-------- CodeWorkspace -------+
              Search/Read
    |
   MySQL
```

## 2. Maven Skeleton
```text
DPOMAgent/
├── pom.xml
├── AGENTS.md
├── CLAUDE.md
├── README.md
├── checkstyle.xml
├── .editorconfig
├── openspec/
├── docs/{architecture,tasks}/
├── agent-common/
├── agent-adapter/
│   ├── agent-adapter-llm/
│   ├── agent-adapter-runtime/
│   └── agent-adapter-codegraph/
├── agent-core/
│   └── incident/investigation/hypothesis/observation/conclusion/workspace/tool/script/persistence
└── agent-web/
    └── controller/dto/config/exception + application.yml + db/migration
```

## 3. Dependency
`agent-web -> agent-core -> adapter.{llm,runtime,codegraph} -> agent-common`
adapters 互不依赖；web 不直接调外部系统；Provider/remote DTO 不泄漏。

## 4. Core Model
- Incident: serviceCode/environment/releaseVersion/commitSha/symptom
- Investigation: status/budget/currentRun
- InvestigationRun: model/prompt/toolset version
- InvestigationStep: append-only
- Observation: source/artifact/location/supports/contradicts
- Hypothesis: parent/description/status/missingChecks
- Conclusion: resultType/rootCause/evidenceIds/unresolvedQuestions
- ScriptArtifact: type/language/purpose/risk/readOnly/approval/preconditions/verification/rollback/content

## 5. State Machine
```text
CREATED -> SCOPING -> RESEARCHING -> FORMING_HYPOTHESES -> VALIDATING
             ^                                    |
             |                                    v
             +----------- WAITING_FOR_HUMAN <-----+
                                      |
                                      v
                                 SYNTHESIZING
                                      |
                 COMPLETED / INCONCLUSIVE / FAILED / CANCELLED
```

## 6. Decision Loop
1. 读取当前状态和 Observation；
2. 比较竞争 Hypothesis；
3. 选择区分度最高的下一条证据；
4. 执行一个 bounded tool action；
5. 转换为 Observation；
6. 更新 Hypothesis；
7. 检查 budget/no-progress；
8. 继续或 SYNTHESIZING。

## 7. Code Investigation
DPOMCodeGraphService 返回 snapshotId/workspace/cgc context。
V1 假设与 DPOMAgent 位于同一研发环境，Agent 可读取配置允许根目录内的 Workspace。
Workspace tools：listFiles/searchText/readSource。
Graph tools：symbol/callers/callees/callChain/hierarchy。
不得提供任意 shell。

原则：**文件系统负责读代码，CodeGraph 负责导航，LLM 负责理解。**

## 8. First Symptom Playbook
“创建设备成功但 DB 无记录”初始业务路径：
HTTP -> Controller -> Validation/Branch -> Service -> Transaction -> Repository/Mapper -> INSERT -> COMMIT -> DB

候选方向：
H1 请求未到；H2 假成功/提前返回；H3 Repository 未调用；H4 SQL 异常被捕获；
H5 rollback；H6 datasource/schema/tenant 错；H7 async 失败；H8 read-side 看不到；H9 发布回归。

## 9. Dynamic Script
证据缺口 -> LLM draft -> ScriptPolicyValidator -> SRE。
READ_ONLY_DIAGNOSTIC 可生成 ps/top/ss/grep/jcmd read-only/SELECT/Python parser。
包含 UPDATE/DELETE/INSERT/restart/kill/改配置等必须升级为 MITIGATION。
MITIGATION 只生成 Artifact，必须 REQUIRES_APPROVAL，不提供 execute endpoint。

## 10. MySQL
最少表：
incident, investigation, investigation_run, investigation_step, observation,
hypothesis, conclusion, script_artifact, tool_call_audit。
不存巨量完整日志/源码；保存 ArtifactRef 与摘要。

## 11. REST
POST /api/v1/incidents
POST /api/v1/investigations
POST /api/v1/investigations/{id}/run
GET  /api/v1/investigations/{id}
GET  /api/v1/investigations/{id}/timeline
POST /api/v1/investigations/{id}/artifacts
POST /api/v1/investigations/{id}/scripts/{scriptId}/result
GET  /api/v1/scripts/{scriptId}

## 12. Explicitly No Knowledge
当前工程不创建 knowledge/rag/embedding/vector/memory 模块或依赖。未来单独 Change。

## 13. Milestones
M0 skeleton+DB；M1 LLM+state machine；M2 code workspace+stacktrace；
M3 runtime evidence+无堆栈场景；M4 diagnostic script feedback；M5 mitigation artifact。

## 14. DoD
单 Java Web；MySQL 可恢复；LLM/Runtime/CodeGraph Adapter；Workspace 安全；
Hypothesis/Observation 可追踪；两类 Case 跑通；脚本分级；无 Knowledge/RAG；
无 Docker/K8s/Redis/Kafka；`mvn clean verify` 通过。
