# DPOMAgent V1 Architecture — 双区域闭环（No Knowledge）

DPOMAgent 是**同一套诊断引擎**，两种部署 Profile：`production`（生产区域）与 `development`（研发区域），
通过受控的 Diagnostic Evidence Package 经 OBS 单向交接形成闭环。本期无 Knowledge/RAG/Embedding/Vector DB。

```text
                            生产区域（production profile）
SRE/告警
  |
  v
DPOMBaseMCPServer (只读华为云证据网关，不做 LLM/RCA/源码分析)
  |-- AOM / CES / APM / LTS / CCE 证据标准化
  v
DPOMAgent (production profile，单实例 Java Web)
  |-- 调查编排 / 时间线 / 证据关联 / 假设与置信度
  |-- EscalationPolicy（结论 or 升级判断）
  |     +-- 置信度足够 -> 本地结论
  |     +-- 证据不足 -> INCONCLUSIVE + missingEvidence -> 证据包（经审批后）
  |-- EvidenceHandoffPackage（版本化、限量、脱敏、checksum）
          |
          | 显式 approval gate（与 escalationEligible 分离）
          v
      OBS（allow-list bucket/prefix、服务端加密、不可预测对象名、审计、生命周期、checksum）
          |
          | 下载端先校验 schema/version/checksum/大小
          v
                            研发区域（development profile）
DPOMAgent (development profile，集中一套，单实例 Java Web)
  |-- EvidenceHandoffVerifier（fail closed）
  |-- 恢复为 EvidenceBundle / 调查输入
  |-- Repository Registry + DPOMCodeGraphService/CodeGraph + 精确 release/commit 源码快照 + 研发侧 LLM
  |-- 结合准确源码做最终 RCA
```

## 生产区域约束

- 不要求原始源码；允许与发布版本匹配、边界裁剪后的 CodeGraph 结果。
- 禁止 RAG/Embedding/Vector DB；禁止任意 Shell；禁止生产写操作；禁止自动执行修复或生成脚本。
- 只允许人审批后的显式动作；`escalationEligible` 与 OBS 上传批准是分离的两件事。

## 研发区域约束

- 集中部署一套 development profile，不为每个仓库部署独立 Agent。
- CodeGraph（`colbymchenry/codegraph`）是源码导航/结构化上下文，不包装成向量检索或 RAG。
- 证据包经校验后才能进入调查；错误包 fail closed。

## 跨区域交接约束

- 只上传版本化、限量、脱敏的 Diagnostic Evidence Package。
- 禁止源码、AK/SK、Token、Cookie、原始大批量日志、无边界 dump。
- 复用既有 EvidenceBundle、LogRedactor、调查状态机；不另造平行模型。
