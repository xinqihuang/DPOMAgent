# T211 告警变更管控 Web UI

## Goal

在 `agent-web` 托管一个浅色、响应式的华为云告警变更管控页面，覆盖 AOM、统一 APM 和 CES 的预检、审批、屏蔽、恢复、重试、规则结果与审计时间线。

## Boundaries

- HuaweiCloudAlarmChangeGuard 仍是唯一写控制面和审计事实源。
- DPOMAgent 托管静态资源，并提供固定目标、固定 operations 路径的透明转发；不实现业务逻辑或云 Provider。
- 页面只调用部署配置的 HTTPS Change Guard 地址；本地开发允许 localhost HTTP。
- 页面不提供登录、会话或凭据输入，也不自行添加 Authorization 请求头。
- 不引入 Node、React/Vue、外部字体/图标、数据库迁移或新基础设施。

## Test First

1. 静态资源契约：页面、CSS、配置和 ES modules 可由 Spring MVC 提供。
2. 安全契约：配置无秘密、无凭据输入、无动态 HTML 注入、无 DPOMAgent 写路由。
3. 客户端契约：固定 API 地址、相对路径、无 Authorization 请求头、写幂等键、未知状态零写动作。
4. 浏览器验收：桌面/窄屏、键盘焦点、确认对话框和错误/部分恢复状态。

## Acceptance

- [x] `/change-guard/` 在 production/development profile 均可访问。
- [x] 浅色桌面双栏和窄屏单栏均无布局遮挡，关键动作可达。
- [x] 可输入三类精确规则并展示冻结清单、原状态、步骤状态和 request ID。
- [x] 审批、屏蔽、恢复、重试均使用新幂等键；屏蔽前显示精确范围并二次确认。
- [x] 所有可恢复状态都显示高可见度恢复入口，未知状态禁用写动作。
- [x] 页面不包含建立会话、Bearer Token 输入或 Authorization 请求头。
- [x] 页面不含 AK/SK/Token 默认值，不使用第三方脚本或动态 HTML 注入。
- [x] DPOMAgent 只提供固定 Change Guard operations 转发，不接受动态目标且不实现业务写逻辑。
- [x] `mvn clean verify` 与 `mvn -pl agent-web -am package` 通过。
