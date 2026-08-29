## 1. 规则作用域输入

- [x] 1.1 抽出纯函数 `ruleSelector`，企业项目 ID 空值语义为“不过滤”，补 node 单测
- [x] 1.2 规则行新增「企业项目 ID」输入框，删除 `collectRules` 硬编码的 `"0"`，缓存令牌升级
- [x] 1.3 Java 契约测试锁定 `rule-eps` 字段存在且 app.js 不再硬编码企业项目
- [x] 1.4 `mvn verify` 与 node 测试全部通过
