## 1. 任务契约与安全基线

- [x] 1.1 新增 `docs/tasks/T211-alarm-change-guard-web-ui.md`，记录独立 Change Guard 边界、浅色视觉目标、测试先行步骤和逐项 Acceptance
- [x] 1.2 测试先行增加静态资源契约测试，验证 `/change-guard/` 页面、CSS、ES modules 和无秘密默认 `config.js` 可访问，且 production/development profile 均没有新增 Change Guard 写控制器
- [x] 1.3 测试先行定义客户端安全断言：只接受部署配置的 HTTPS 基础地址或 localhost 本地开发地址，不提供凭据输入或 Authorization 请求头，远端文本不通过动态 HTML 注入

## 2. API 客户端与状态模型

- [x] 2.1 实现无框架 ES module API client，集中处理相对路径约束、每次写操作的新幂等键、JSON/空响应和脱敏错误映射
- [x] 2.2 实现当前操作客户端状态模型，按服务端状态计算审批、屏蔽、恢复和重试动作可用性；未知状态默认禁用写动作
- [x] 2.3 使用可控 Change Guard HTTP stub 或等价浏览器模块测试覆盖创建、审批、屏蔽、恢复、重试、详情、审计以及网络/CORS 和业务错误

## 3. 浅色响应式操作界面

- [x] 3.1 创建语义化页面骨架和浅色设计 token，完成桌面双栏、860px 以下单栏、规则表格卡片化、文本化状态徽标以及不依赖外部字体/图标的视觉系统
- [x] 3.2 页面直接使用固定 API 地址且不提供会话或凭据输入；配置无效时零业务请求并显示配置错误
- [x] 3.3 实现变更单、窗口、恢复截止时间与 AOM/APM/CES 精确规则编辑器，提交预检后展示操作 ID、摘要、原状态统计和冻结清单
- [x] 3.4 实现审批、屏蔽确认、恢复和失败重试动作；屏蔽对话框必须展示操作 ID、变更单、区域、项目、服务分布、规则数量和恢复截止时间
- [x] 3.5 实现规则级结果、上游 request ID、审计时间线以及加载/空/成功/失败/未授权反馈，确保恢复入口在所有可恢复状态中保持高可见度
- [x] 3.6 完成键盘焦点顺序、对话框焦点管理、`aria-live` 异步通知、非纯颜色状态表达和窄屏关键动作可达性验证

## 4. 部署与验收

- [x] 4.1 增加 DPOMAgent 静态资源 CSP 与 Change Guard 精确 CORS allowlist 配置说明，明确 AK/SK 和写请求不进入 DPOMAgent 后端
- [x] 4.2 在浅色桌面和窄屏视口执行浏览器视觉验收，覆盖正常生命周期、预检失败和补偿/部分恢复场景，并保存截图或验收记录
- [x] 4.3 运行 `mvn clean verify` 和 `mvn -pl agent-web -am package`，确认全部既有测试与新增 UI 契约通过且无生产写控制器、前端秘密或额外基础设施

## 5. 范围修订

- [x] 5.1 删除建立安全会话、Bearer Token 输入、前端 Authorization 处理及相关文案，更新契约测试、部署文档并重新完成桌面/窄屏验收
- [x] 5.2 增加仅允许固定 Change Guard `/api/v1/operations` 的同源转发入口，消除本地浏览器跨端口网络错误并完成连通性验收
- [x] 5.3 修正审计时间线字段契约：按 Change Guard `AuditEvent` 真实字段（`createdAt`/`eventType`/`actor`/`beforeState`/`afterState`/`ruleKey`/`details`）渲染，新增纯函数 `auditEntry` 与回归测试，并把 `dpom.change-guard.base-url` 显式声明进 `application.yml`
