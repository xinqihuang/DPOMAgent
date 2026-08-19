# 告警变更管控 UI 验收记录

日期：2026-08-19

## 浏览器视觉验收

- 桌面视口：1440 × 900，工作区为双栏，页面宽度 1425px，无横向溢出。
- 窄屏视口：390 × 844，工作区折叠为 351px 单栏，无横向溢出，连接与创建入口可见。
- 页面标题、浅色视觉、AOM/APM/CES 规则编辑区、生命周期操作区、规则结果和审计区均正常渲染。
- 浏览器控制台在桌面和窄屏加载后均无 warning/error。
- 页面已在浏览器中恢复至 1440 × 900，供人工继续检查。

## 状态与安全验收

- 客户端模块测试覆盖：HTTPS 地址约束、无 Authorization 请求头、每次写操作的新幂等键、未知状态 fail-closed。
- 状态模型与界面覆盖创建、审批、屏蔽确认、恢复、失败重试、预检失败、部分恢复及重新认证反馈。
- Spring MVC 契约验证静态页面、CSS 与 ES modules 可访问；固定转发只允许 Change Guard `/api/v1/operations` 路径，其他路径返回 404。
- 静态资源扫描确认没有建立会话、凭据输入、Authorization 请求头、AK/SK/Token 默认值或动态 HTML 注入。

## 自动化验证

- `mvn clean verify`：通过；8 个 reactor 模块全部 SUCCESS；`agent-web` 161 个测试，0 失败、0 错误、25 个按既有条件跳过。
- `mvn -pl agent-web -am package -DskipTests`：通过；8 个 reactor 模块全部 SUCCESS。
- `node --test agent-web/src/test/js/change-guard-client.test.mjs`：6/6 通过。

## 交接复核（任务 5.3）

对照 `HuaweiCloudAlarmChangeGuard` 真实源码逐项复核 UI 读写契约，结论与修正如下：

- API 路径一致：`POST /api/v1/operations`、`/{id}/approvals`、`/{id}/shield`、`/{id}/restore`、`/{id}/restore/retry`、
  `GET /{id}`、`GET /{id}/audit` 与 `ChangeGuardController` 完全对应。
- 状态机一致：`state.js` 的可恢复/可重试集合是 `OperationState` 的真实子集，且与 `TRANSITIONS` 允许的迁移一致；未知状态仍 fail-closed。
- 规则与尝试字段一致：`identity.{source,region,projectId,upstreamRuleId,upstreamRuleName}`、
  `originalEnabled`、`lastKnownEnabled`、`stepState`、`attempts[].{ruleSnapshotId,upstreamRequestId}` 均与 `RuleSnapshot`/`GuardAttempt` 对应。
- **已修正的缺陷**：审计时间线原先读取 `occurredAt` 与 `result`，这两个字段在 `AuditEvent` 中并不存在（全仓库仅前端出现），
  导致每条审计事件的时间列恒为 “—” 且丢失状态迁移信息。现改为按真实字段 `createdAt`、`eventType`、`actor`、
  `beforeState → afterState`、`ruleKey`、`details` 渲染，逻辑抽为纯函数 `auditEntry` 并由 node 测试回归覆盖。
- **已补齐的配置**：`dpom.change-guard.base-url` 此前只存在于控制器 `@Value` 默认值，现显式声明进
  `application.yml`（`DPOM_CHANGE_GUARD_BASE_URL` 可覆盖），与其他外部能力的配置口径一致。
- 认证边界确认：Change Guard 的 API 由 JWT + `@PreAuthorize` 保护，本地开发依赖其
  `local-permit-all` 模式按路径注入 requester/approver/executor/auditor 角色，因此页面不持有凭据仍可完成本地闭环；
  同时满足 `GuardOperation.approve` 的“申请人不得自审批”约束。
- 静态资源缓存令牌统一升级为 `v=20260819-3`，避免浏览器沿用旧的 `app.js`/`state.js`。
