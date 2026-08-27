## MODIFIED Requirements

### Requirement: Persistent Investigation
系统 SHALL 持久化 Incident/Investigation/Run/Step/Observation/Hypothesis/Conclusion。进入需要评测交接的终态时，
系统 SHALL 在同一数据库事务中持久化终态、Conclusion、Run 完成和对应 canonical Diagnosis Event outbox 行；应用
重启后 SHALL 从这些持久化事实恢复，且不依赖原 LLM 会话。

#### Scenario: Resume
- **GIVEN** 调查已进行数步
- **WHEN** 应用重启
- **THEN** 系统 SHALL 从数据库恢复状态
- **AND** 不依赖原 LLM 会话

#### Scenario: Atomic terminalization
- **GIVEN** Investigation 产生可持久化的终态 Conclusion
- **WHEN** 系统提交终态
- **THEN** Investigation 状态、Conclusion、Run 完成和唯一 outbox event SHALL 原子可见
- **AND** outbox 网络投递 MUST NOT 发生在该数据库事务内

#### Scenario: Terminal transaction rolls back
- **GIVEN** Conclusion、Run 完成或 outbox event 任一写入失败
- **WHEN** 终态事务回滚
- **THEN** 系统 MUST NOT 暴露缺少对应 canonical event 的终态 Investigation
- **AND** 后续恢复 SHALL 能安全重试终态提交
