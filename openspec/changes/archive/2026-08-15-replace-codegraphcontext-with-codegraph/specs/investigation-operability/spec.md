## MODIFIED Requirements

### Requirement: Health and readiness endpoints
系统 SHALL 通过 Spring Boot Actuator 暴露标准 liveness 与 readiness 探针，并保留现有 `/api/v1/health` 的返回结构（status/db/executor/external）兼容语义。

#### Scenario: Liveness probe
- **WHEN** 请求 `/actuator/health/liveness`
- **THEN** 进程存活时 SHALL 返回 200 + UP，且该 group 仅含 livenessState，不因外部 DeepSeek/Drain3/CodeGraph 瞬时不可用而 DOWN

#### Scenario: Readiness probe
- **WHEN** 请求 `/actuator/health/readiness`
- **THEN** readiness group SHALL 由 readinessState + DB 检查 + executor 容量组成；UP 时返回 200，DOWN/OUT_OF_SERVICE 时返回 503；外部被动健康指示器 MUST NOT 参与该 group

#### Scenario: Actuator endpoint exposure
- **WHEN** 访问 Actuator 端点
- **THEN** SHALL 仅暴露 `/actuator/health`（含 liveness/readiness）与 `/actuator/prometheus`；MUST NOT 暴露通用 `/actuator/metrics`

#### Scenario: Legacy health compatibility
- **WHEN** 请求 `/api/v1/health`
- **THEN** SHALL 返回与既有结构兼容的 status/db/executor/external 字段

### Requirement: Adapter metrics
系统 SHALL 记录外部适配器（DeepSeek/Drain3/CodeGraph）调用的延迟，标签统一为 `adapter` + `errorCode`。

#### Scenario: Adapter call timer
- **WHEN** 调用外部适配器
- **THEN** SHALL 记录以 adapter 与 errorCode 为标签的计时器（成功 errorCode=NONE、超时=TIMEOUT、其余为有限白名单稳定错误码），MUST NOT 使用 result 标签，且不记录调用参数或响应内容

### Requirement: Passive adapter component status
外部 DeepSeek/Drain3/CodeGraph 的组件状态 SHALL 为被动观测：来自最近一次真实业务调用结果与时间戳；从未调用 SHALL 为 UNKNOWN；状态 MUST 有过期语义；health endpoint MUST NOT 主动调用外部适配器，MUST NOT 暴露 URL、异常文本、响应或凭据。

#### Scenario: Never-called adapter
- **WHEN** 某外部适配器从未被业务调用
- **THEN** 其组件状态 SHALL 为 UNKNOWN

#### Scenario: Stale adapter status
- **WHEN** 最近一次业务调用结果超过过期窗口
- **THEN** 其组件状态 SHALL 归为 UNKNOWN

#### Scenario: No active probe and no leak
- **WHEN** health endpoint 被请求
- **THEN** SHALL NOT 主动调用外部适配器（不产生费用/流量/副作用），且组件详情 MUST NOT 含 URL/异常文本/响应/凭据

