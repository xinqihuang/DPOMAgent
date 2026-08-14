# DPOMAgent V1 Architecture — No Knowledge

```text
SRE
 |
 v
DPOMAgent (single Java Web)
 |-- InvestigationCoordinator
 |-- LLM Adapter ----------> Model Provider
 |-- Runtime Adapter ------> DPOMBaseMCPServer
 |-- CodeGraph Adapter ----> DPOMCodeGraphService
 |                           |-- Snapshot / CGC
 |                           `-- Local Code Workspace
 |-- ScriptArtifactService
 `-- MySQL
     Incident/Run/Step/Observation/Hypothesis/Conclusion/Script/Audit
```

本期无 Knowledge/RAG/Embedding/Vector DB。
