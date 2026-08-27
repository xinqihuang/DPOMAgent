# add-dpomagent-diagnosis-event-outbox 实现交接

## 交付结果

- `COMPLETED` / `INCONCLUSIVE` 的 Investigation、Conclusion、Run、不可变 Diagnosis Event 和 CREATED 审计在一个
  短事务内提交；`FAILED` / `CANCELLED` 只收口调查记录，不产生评测事件。
- Diagnosis Event v1 使用固定契约、显式 provenance、RFC 8785 规范 JSON 和 SHA-256；重试与重放只读取已持久化
  内容，不重新生成身份或正文。
- V12 新增 outbox、追加式 audit 和持久化 replay nonce。MyBatis 使用显式列 XML；终态键、eventId 和幂等键有
  唯一约束。
- 投递生命周期为 PENDING / IN_FLIGHT / DELIVERED / DEAD，包含 CAS 租约、fencing token、过期恢复、确定性有界
  指数退避、最大尝试次数与最大事件年龄。
- RestClient 端口仅允许 HTTPS，设置 connect/read timeout，限制确认体大小，并用 timestamp/method/path/body hash
  计算 HMAC。投递前会重新校验规范正文哈希。
- 内部重放端点与投递分别开关；HMAC 使用固定时间窗、常量时间比较和落库 nonce，只接受
  eventId/operatorRef/reason，并在同一事务内重置 DEAD 事件与追加 OPERATOR_REPLAY 审计。

## 开关与运行约束

- `dpom.evaluation.delivery.enabled=false`（默认）：不装配网络端口、租约编排或调度 worker。
- `dpom.evaluation.replay.enabled=false`（默认）：不装配 `/internal/v1/diagnosis-events/replay`。
- 启用投递必须提供 HTTPS destination、至少 32 字节 HMAC secret 和全部正数边界。
- 启用重放必须提供独立强 secret、正数 timestamp window/nonce TTL/operatorRef/reason 上限。
- secret、签名、规范正文、证据、incident/investigation ID 不进入日志、审计正文或指标标签；指标只使用稳定
  state/result/errorCode。

## 稳定结果与错误码

- 成功确认：`ACCEPTED`、`EQUIVALENT_DUPLICATE` → `DELIVERED`。
- 可重试：timeout、`HTTP_408`、`HTTP_429`、`HTTP_5xx`、`MALFORMED_ACKNOWLEDGEMENT`、
  `ACKNOWLEDGEMENT_TOO_LARGE` → 有预算时 `PENDING`，耗尽后 `DEAD`。
- 永久失败：非重试 4xx、`PERMANENT_REJECTION`、`IDEMPOTENCY_CONFLICT`、
  `CONTENT_INTEGRITY_FAILURE` → `DEAD`。
- 重放认证对外统一返回 `REPLAY_AUTHENTICATION_FAILED`，不区分时间、nonce 或签名失败细节。

## 上线与回滚

1. 先部署 V12，保持 delivery/replay 关闭，验证 eligible 终态产生 PENDING outbox。
2. 配置 secret manager 注入、HTTPS destination、timeout/retry 边界后，仅启用 delivery。
3. 观察 PENDING/IN_FLIGHT/DELIVERED/DEAD 数量和稳定错误码，不使用高基数标签。
4. 只有内部运维面准备完成后才单独启用 replay。
5. 回滚时先关闭 replay，再关闭 delivery；保留 V12 表和不可变事件，旧实例不会访问新网络能力。

## 验证记录

- 2026-08-23 停止占用旧 JAR 的本地 DPOMAgent 开发进程后，在主仓库执行离线 `mvn clean verify`；九模块构建、
  checkstyle、全套测试和 Spring Boot repackage 均通过，共 457 项测试、0 failure/error、28 项显式外部环境 gate skip。
- H2 MySQL-mode 覆盖迁移、唯一约束、事务回滚、并发终态化、租约竞争、fencing、恢复、重试耗尽与重放。
- 契约测试覆盖两个正例、全部负例 fixture、RFC 8785 向量和源资产 SHA-256 manifest。
- HTTP/HMAC、条件装配、低基数指标、持久化 nonce 与 enabled replay 端点均有聚焦测试。
- `openspec validate add-dpomagent-diagnosis-event-outbox --strict`：通过。
- 禁用面扫描：无 Kafka、RAG/Embedding/Vector DB、任意 shell、自动修复或跨服务数据库访问。
- 2026-08-23 使用显式环境注入的本地 MySQL 8 执行 `MybatisExternalMysqlContractTest`，V12 迁移、真实
  lease/update SQL 与约束断言通过，并输出 `REAL_EXECUTED diagnosis-event-outbox MySQL 8 contract`。
