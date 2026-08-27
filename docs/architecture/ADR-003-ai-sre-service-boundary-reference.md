# ADR-003: AI For SRE Phase 1 服务边界引用

- 状态: Accepted
- 日期: 2026-08-21
- 权威决策: `D:\code\ADR.md`

## Context

工作区 Phase 1 目标只有三个后端部署单元。本文记录 DPOMAgent 作为 Phase 1A 兼容运行时的迁移责任。

## Decision

DPOMAgent 仅在明确的迁移窗口内继续作为既有 Incident、Investigation、Run、Step、Observation、Hypothesis、
Conclusion、预算和审计历史的当前权威：

- 运行时证据继续通过 DPOMBaseMCPServer 等受控 Adapter 获取；
- 诊断完成后向 SRE Intelligence 输出不可变、版本化事件或 Replay Bundle；
- 不把 Dataset、Eval Case、Eval Run、JudgeResult 或 Release Gate 建成第二套调查模型；
- 保留事务 Outbox + 幂等 HTTP 的 Diagnosis Event v1 兼容路径；本仓库不引入 Kafka；
- 不执行自动生产缓解，修复建议继续保持为审批约束下的 Artifact。

Phase 1B 通过工作区 change `complete-phase1-three-service-convergence` 将新调查权威切换到
DPOMBaseMCPServer。切换采用 authority epoch、停止接收新调查、drain、验证后切换；不做静默行迁移或无限双写。

## Consequences

- 切换前，本仓库现有 MySQL/Flyway/MyBatis 是既有调查事实来源；切换后继续提供历史查询与回滚证据，
  但不得为新 epoch 创建权威调查。
- 下游评测服务只消费事件投影和 Artifact 引用，不直接访问 DPOMAgent 数据库。
- 现有真实诊断 Regression Runner 是后续 Rule Judge 的复用来源，禁止无依据重写。

## References

- 工作区 ADR：`D:\code\ADR.md`
- OpenSpec change：`D:\code\openspec\changes\complete-phase1-three-service-convergence`
- 中立契约目录：`D:\code\contracts\diagnosis-event\v1`
