# T012 — TOP Case: Device Create Not Persisted
## Goal
将“资产管理界面创建设备成功但 DB 无记录”做成无堆栈验收案例。
## Fixture
sample asset-service：AssetController.create→AssetService.create→AssetRepository.insert。
至少四个 Case：
A 业务分支提前 return；
B INSERT 后事务 rollback；
C 错 tenant/schema；
D 写入成功但查询过滤错误。
## Evidence
每个 Case 固化症状、日志、可选 trace、release commit、expected root cause、expected class/method、补充诊断脚本期望。
## Acceptance
不凭症状直接结论；≥2 Hypothesis；结论引用 Observation；必要时生成 ScriptArtifact；RCA/代码位置与 expected 对齐。
