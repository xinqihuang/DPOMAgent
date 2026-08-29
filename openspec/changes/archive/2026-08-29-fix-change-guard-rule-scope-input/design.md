# 设计：规则作用域输入

- `state.js` 新增 `ruleSelector({source, region, projectId, enterpriseProjectId, upstreamRuleId, expectedName})`
  纯函数：所有字段 trim，`enterpriseProjectId` 默认空串。空串由 Change Guard 网关的 `emptyToNull` 转成 null，
  从而省略 `Enterprise-Project-Id` 请求头（不过滤企业项目）。
- `index.html` 规则行模板在 Project ID 与规则 ID 之间新增 `.rule-eps` 输入，placeholder「留空=全部；0=默认项目」。
- `app.js` 的 `collectRules()` 改为调用 `ruleSelector`，删除硬编码的 `"0"`。
- 缓存令牌升级为 `v=20260819-4` 避免浏览器沿用旧模块。
- 测试：node 单测覆盖 `ruleSelector` 的空值/`0`/UUID 语义；Java 契约测试断言页面含 `rule-eps` 且 app.js 不再硬编码 `"0"`。
