## Why

华为云告警变更管控服务已经提供 AOM、统一 APM 与 CES 的预检、审批、屏蔽、恢复和审计 API，但缺少面向实施人员的可视化操作入口。需要在 DPOMAgent 的 `agent-web` 模块补充一个清晰的浅色系界面，让受权用户能够看到精确规则清单与风险状态，并以显式动作完成完整生命周期。

## What Changes

- 在 `agent-web` 中新增浅色系“告警变更管控”单页操作界面，支持桌面与窄屏布局。
- 使用部署时配置的 Change Guard 服务地址；页面不提供登录、会话或 Bearer Token 输入，认证如有需要由部署边界统一处理。
- 覆盖创建/预检、提交审批、执行屏蔽、人工恢复、失败项重试、状态刷新与审计时间线展示。
- 对屏蔽等高风险动作展示精确规则数量、服务类型、区域、项目和恢复截止时间，并要求二次确认；恢复动作保持醒目且无需二次审批。
- DPOMAgent 托管静态资源，并通过仅允许 `/api/v1/operations` 的固定同源转发入口调用独立 Change Guard API；不在 DPOMAgent 中引入华为云凭据或生产写适配器。
- 增加静态资源、交互状态、无障碍、安全边界和浏览器/API stub 验收测试。

## Capabilities

### New Capabilities

- `alarm-change-guard-ui`: 定义 DPOMAgent 托管的华为云告警变更管控界面、交互流程、视觉状态和客户端安全边界。

### Modified Capabilities

无。

## Impact

- 影响 `agent-web/src/main/resources/static`、固定 Change Guard 转发控制器、相关测试及运维文档。
- 依赖既有 HuaweiCloudAlarmChangeGuard REST/OpenAPI 契约；不修改其服务端行为。
- 部署时需为 Change Guard API 配置允许 DPOMAgent 页面源站的精确 CORS allowlist；认证策略属于 Change Guard 的部署边界，不进入本页面交互范围。
- 不新增前端框架、Node 构建链、数据库表或常驻基础设施。
