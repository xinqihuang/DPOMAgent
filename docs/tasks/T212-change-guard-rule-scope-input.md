# T212 告警变更管控规则作用域输入

## Goal

规则编辑器支持显式填写企业项目 ID；留空表示不按企业项目过滤，覆盖非默认企业项目下的真实 AOM 规则，消除 `RULE_NOT_FOUND` 误报。

## Boundaries

- 仅改 agent-web 静态资源与 JS，不改变固定转发边界，不新增后端写逻辑。
- 不引入 Node/框架/新依赖。

## Test First

1. `ruleSelector` 纯函数：空值→空串（不过滤）、`"0"`→默认项目、UUID 保留。
2. 页面含 `.rule-eps` 输入，app.js 不再硬编码 `enterpriseProjectId: "0"`。

## Acceptance

- [x] 规则行有「企业项目 ID」输入框，留空/填 0/填 UUID 三种语义正确。
- [x] node --test 全通过；mvn verify 全通过。
