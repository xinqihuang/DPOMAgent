# Phase 1B 架构边界门禁

记录时间：2026-08-25（Asia/Shanghai）

## 可执行门禁

- `D:\code\scripts\verify-phase1-service-boundaries.ps1`：跨仓扫描服务数据库引用、provider SDK/凭据、
  DeepEval 持久化、RAG/vector 依赖和生产写工具 fail-closed 注解。
- `DPOMBaseMCPServer/agentic-monitoring/.../MonitoringArchitectureTest`：拒绝 provider SDK/凭据对象、
  SQL/MyBatis/Kafka、其他服务实现和 DeepEval 类型进入证据编排层。
- `SREIntelligenceService/sre-core/.../CoreArchitectureTest`：保持 core 无框架、无 SQL、无 Kafka/provider/model SDK。
- `SREIntelligenceService/sre-web/.../ServiceBoundaryArchitectureTest`：拒绝其他服务/provider 实现依赖，
  并禁止 Ingestion 类依赖 DeepEval client 或 semantic domain。
- `DeepEvalService/tests/test_architecture.py`：拒绝持久化、Artifact 下载、生产云/数据库凭据、RAG/vector/cloud SDK 依赖。
- `WriteToolRegistrationTest`：证明 CES 历史写工具默认关闭、单 gate 不生效，且 production profile 即使双 gate
  也不注册；仅隔离的非 production `action-enabled` 场景可注册。

## 当前验证结果

| Gate | 结果 |
|---|---|
| 工作区边界扫描 | `PHASE1_BOUNDARY_SCAN=PASS`；44 DPOM neutral files、208 SRE production files、14 DeepEval production files、2 write-tool gates |
| DPOMBase `mvn verify` | PASS；456 tests，0 failures/errors/skips |
| SRE `mvn verify` | PASS；129 tests，0 failures/errors，4 skipped |
| DeepEval `pytest -q` | PASS；24 tests |
| OpenSpec strict validation | PASS |

门禁不扫描本机凭据文件、虚拟环境、构建输出或历史文档；它针对 production sources、依赖声明和明确的
composition/tool boundary。后续新增 `agentic-diagnosis`、`agentic-persistence`、`agentic-messaging` 时会自动进入
工作区扫描范围。
