# T011 — Mitigation Script Artifact
## Goal
支持输出修复脚本/方案，但 DPOMAgent 不执行。
## Requirements
type=MITIGATION；approvalStatus=REQUIRES_APPROVAL；必须含 rootCause/evidence、target、purpose、risk、preconditions、script、verification、rollback。
## Guardrails
无 execute endpoint；不 SSH；不调用生产写 API。
## Acceptance
生成一个示例 Mitigation Artifact；代码搜索确认不存在自动执行路径。
