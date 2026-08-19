## MODIFIED Requirements

### Requirement: 创建预检与冻结清单展示

页面 SHALL 接受变更单号、变更窗口、恢复截止时间和 AOM/APM/CES 精确规则引用，调用创建接口，并 SHALL 在任何屏蔽动作可用前展示后端返回的操作 ID、清单摘要、每条规则原始状态和当前步骤状态。规则引用 MUST 支持显式填写企业项目 ID，且留空时 SHALL 不按企业项目过滤（覆盖非默认企业项目规则）。

#### Scenario: 预检成功
- **GIVEN** 用户具有申请人权限并填写有效变更信息和规则引用
- **WHEN** 用户提交创建预检
- **THEN** 页面 SHALL 使用新的幂等键创建操作，并 SHALL 展示冻结清单、原本启用/停用统计和待审批状态

#### Scenario: 预检失败
- **GIVEN** 任一规则无法解析或 API 返回错误
- **WHEN** 创建请求结束
- **THEN** 页面 MUST 不启用屏蔽动作，并 SHALL 展示脱敏错误码、关联 ID 和可执行的修正提示

#### Scenario: 非默认企业项目规则
- **GIVEN** 一条 AOM 告警规则属于非默认企业项目
- **WHEN** 用户填写其企业项目 ID 或留空并按精确规则 ID 创建预检
- **THEN** 页面 SHALL 把该企业项目 ID（或空串表示不过滤）传给 Change Guard
- **AND** Change Guard SHALL 能解析到该规则，不再误报 `RULE_NOT_FOUND`
