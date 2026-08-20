## Purpose

为实施人员提供由 DPOMAgent 托管、但与诊断编排和华为云凭据隔离的告警变更管控界面，使 AOM、统一 APM 与 CES 的预检、审批、屏蔽、恢复和审计操作清晰、受控且可验证。

## ADDED Requirements

### Requirement: 浅色响应式操作界面

系统 SHALL 在 DPOMAgent `agent-web` 中提供浅色系告警变更管控页面，并 MUST 在桌面和窄屏视口中保持操作步骤、状态、规则表格和主要动作可读可用。

#### Scenario: 打开操作界面
- **GIVEN** DPOMAgent `agent-web` 正常运行
- **WHEN** 用户访问告警变更管控页面
- **THEN** 系统 SHALL 展示创建预检、审批执行、恢复和审计区域，并 SHALL 使用清晰的浅色视觉层级标识普通、警告、危险和成功状态

#### Scenario: 窄屏访问
- **GIVEN** 用户使用窄屏设备访问页面
- **WHEN** 视口不足以横向容纳桌面布局
- **THEN** 系统 MUST 重排表单与状态区，规则数据 MUST 可阅读且关键动作 MUST 无需横向滚动即可触达

### Requirement: 固定 API 目标与无凭据界面

页面 MUST 只向 DPOMAgent 的固定同源 Change Guard operations 转发路径发起请求。页面 MUST NOT 提供登录、建立会话、Bearer Token、AK 或 SK 输入，也 MUST NOT 自行添加 Authorization 请求头；转发路径 MUST NOT 接受可变目标地址，DPOMAgent MUST NOT 持有华为云凭据。

#### Scenario: 页面直接使用固定 API
- **GIVEN** 页面配置了 DPOMAgent 同源 Change Guard 转发地址
- **WHEN** 用户打开页面或发起业务操作
- **THEN** 页面 SHALL 直接使用该地址，且 MUST 不要求用户建立安全会话或输入任何凭据，转发端 SHALL 只允许 `/api/v1/operations` 路径

#### Scenario: API 地址无效
- **GIVEN** 部署配置缺失、非 HTTPS 且不是 localhost 本地开发地址，或不符合允许的 Change Guard API 地址
- **WHEN** 页面初始化或用户发起业务操作
- **THEN** 页面 MUST 阻止业务请求并展示不包含凭据的配置错误

### Requirement: 创建预检与冻结清单展示

页面 SHALL 接受变更单号、变更窗口、恢复截止时间和 AOM/APM/CES 精确规则引用，调用创建接口，并 SHALL 在任何屏蔽动作可用前展示后端返回的操作 ID、清单摘要、每条规则原始状态和当前步骤状态。

#### Scenario: 预检成功
- **GIVEN** 用户具有申请人权限并填写有效变更信息和规则引用
- **WHEN** 用户提交创建预检
- **THEN** 页面 SHALL 使用新的幂等键创建操作，并 SHALL 展示冻结清单、原本启用/停用统计和待审批状态

#### Scenario: 预检失败
- **GIVEN** 任一规则无法解析或 API 返回错误
- **WHEN** 创建请求结束
- **THEN** 页面 MUST 不启用屏蔽动作，并 SHALL 展示脱敏错误码、关联 ID 和可执行的修正提示

### Requirement: 审批和屏蔽安全门槛

页面 SHALL 支持提交与当前操作 ID、清单摘要和到期时间绑定的审批。屏蔽动作 MUST 仅在页面拥有已批准详情时可用，并 MUST 在发送请求前要求用户确认精确范围和恢复截止时间。

#### Scenario: 提交审批
- **GIVEN** 页面已加载待审批操作和清单摘要
- **WHEN** 授权审批人提交审批有效期
- **THEN** 页面 SHALL 使用新的幂等键调用审批接口并刷新操作状态

#### Scenario: 确认并执行屏蔽
- **GIVEN** 操作状态为已批准
- **WHEN** 执行人选择屏蔽
- **THEN** 页面 MUST 展示操作 ID、三类服务分布、规则数量、区域、项目和恢复截止时间，并 MUST 仅在用户明确确认后调用屏蔽接口

#### Scenario: 屏蔽结果不完整
- **GIVEN** 屏蔽请求返回失败、补偿中或需人工处置状态
- **WHEN** 页面处理响应
- **THEN** 页面 MUST 不显示“可开始变更”，并 SHALL 突出规则级失败、补偿状态和恢复入口

### Requirement: 恢复与失败重试

页面 SHALL 为所有可恢复状态提供人工恢复入口，并 SHALL 为部分恢复状态提供仅重试失败项入口。恢复动作 MUST 不要求新的审批，且恢复操作的视觉优先级 MUST 不低于屏蔽操作。

#### Scenario: 实施完成后恢复
- **GIVEN** 操作处于已屏蔽或其他可恢复状态
- **WHEN** 执行人选择恢复
- **THEN** 页面 SHALL 使用新的幂等键调用恢复接口、刷新规则级状态，并在全部恢复后显示 `RESTORED`

#### Scenario: 重试未恢复规则
- **GIVEN** 操作处于部分恢复或需恢复状态
- **WHEN** 执行人选择重试
- **THEN** 页面 SHALL 调用恢复重试接口，并 SHALL 保留已成功规则结果且突出仍失败的规则

### Requirement: 状态查询、审计和可访问反馈

页面 SHALL 支持按操作 ID重新加载详情和审计时间线，展示加载、空、成功和错误状态。状态变化 MUST 同时通过文本、图标或结构表达，不能只依赖颜色；键盘焦点和屏幕阅读器 MUST 能识别主要动作与异步结果。

#### Scenario: 查询详情和审计
- **GIVEN** 用户具有相应查询或审计角色
- **WHEN** 用户输入操作 ID 并刷新
- **THEN** 页面 SHALL 展示操作状态、规则级结果、上游 request ID 和有序审计事件，且 MUST 不展示 AK、SK、Token 或签名材料

#### Scenario: 键盘完成关键流程
- **GIVEN** 用户仅使用键盘操作页面
- **WHEN** 用户依次完成预检、确认和恢复
- **THEN** 所有交互控件 MUST 可聚焦、焦点顺序 MUST 合理，确认对话框 MUST 管理焦点且异步结果 MUST 通过可感知状态区域通知
