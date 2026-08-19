# 修复告警变更管控规则作用域：企业项目 ID 可配置

## Problem

UI 在构造规则引用时把 `enterpriseProjectId` 硬编码为 `"0"`（默认企业项目），页面没有输入框。
对于属于非默认企业项目的真实 AOM 告警规则，AOM 的 `ListMetricOrEventAlarmRule` 只返回默认企业项目规则，
导致匹配不到该规则并报 `RULE_NOT_FOUND`，即使规则 ID 真实存在。

## Change

- 规则行新增「企业项目 ID」输入框；留空时不按企业项目过滤（等价于查询全部），填 `0` 表示默认企业项目，填 UUID 精确指定。
- 抽出纯函数 `ruleSelector` 统一构造规则引用载荷，空值默认不留过滤条件。

## Scope

- 仅改动 `agent-web` 静态页面与 JS 模块，不改变转发边界、不新增后端写逻辑、不引入新依赖。

## Non-goals

- 不实现规则列表/自动补全，不读取或缓存任何云凭据。
