# T002 — MySQL Investigation Schema
## Goal
建立最小可恢复 Investigation 持久化。
## Tables
incident, investigation, investigation_run, investigation_step, observation, hypothesis, conclusion, script_artifact, tool_call_audit。
## Requirements
Flyway；JdbcClient DAO；Step append-only；Observation 支持 source/artifact/location；不保存完整源码/巨量日志。
## Tests First
先写 migration/DAO integration test。
## Acceptance
空库 migration 成功；创建 Investigation→Run→Step→Observation→Hypothesis；模拟重启后可恢复。
