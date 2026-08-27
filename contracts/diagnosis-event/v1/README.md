# Diagnosis Event Contract v1

本目录是 DPOMAgent 与 SRE Intelligence Service 之间的中立契约资产。JSON Schema 是字段与结构的权威定义；
Java/Python 绑定必须由该契约生成或用相同正反 fixtures 做兼容测试，不能由某个服务的内部 DTO 反向定义契约。

## 兼容策略

- `schemaVersion` 使用 `major.minor`；本目录只接受 `1.x`。
- 增加可选字段、扩大已定义枚举以外的向后兼容调整可以提升 minor。
- 删除字段、改变字段含义/类型、收紧既有合法输入或改变幂等语义必须提升 major。
- 消费者对已声明支持的旧 minor 使用显式、确定性 upcaster，并同时保存源版本和 upcast 版本。
- 未知 major 返回 `UNSUPPORTED_SCHEMA`，禁止部分处理或猜测缺失字段。

## 规范化、Hash 与幂等

1. 用 RFC 8785 JSON Canonicalization Scheme 对完整 canonical event 规范化；传输 Header、Kafka Header、Outbox
   行号等包装元数据不进入 canonical event。
2. 对规范化 UTF-8 字节计算小写十六进制 SHA-256，作为 canonical content hash。
3. `idempotencyKey` 在一次逻辑诊断结果的首次投递、重试和人工 Replay 中保持不变。
4. 相同 key + 相同 content hash 返回第一次处理结果，不重复创建 Eval Case 或启动 Judge。
5. 相同 key + 不同 content hash 返回 `IDEMPOTENCY_CONFLICT`，保留原事件并记录不含事件正文的审计项。

`eventId` 标识事件事实；重试复用原 `eventId`。如果业务产生了新的调查事实，应创建新 `eventId`、新
`idempotencyKey` 并增加 `aggregateSequence`，不得覆盖旧事件。

## 顺序与 Replay

- `aggregateSequence` 在单个 `investigationId` 内从 1 单调增加。
- 下一序号可直接应用；旧序号按等价重复或陈旧事件处理；出现 gap 时进入隔离/待补状态并暴露
  `SEQUENCE_GAP`，禁止静默越过。
- Replay 从原始持久化事件和 Artifact 读取，不依赖原 LLM 会话，也不修改事件身份或 Provenance。

## Payload 与 Artifact 边界

- 每个事件必须且只能包含 `inlinePayload` 或 `artifactRef` 之一。
- 完整事件最大 64 KiB；`inlinePayload` 规范化后最大 16 KiB。这两项由离线 validator 和服务入口共同检查。
- Artifact 最大 50 MiB，使用受控 location type + opaque locator；禁止客户端传 bucket、AK/SK、任意 OBS key、
  Windows/UNC 路径、反斜杠或 `..` 路径穿越。
- 消费者在 Judge 前校验 media type、byteSize、SHA-256 和 artifact schema version。

## Provenance

必须显式覆盖 application、model、prompt、skills、toolContracts、source 和 evidenceSchema。确实不适用或历史数据
缺失时使用 `status=unavailable` 和机器可读 reasonCode，禁止省略、猜测或使用空字符串。严格 Release Gate 可
排除 Provenance 不完整的案例，但仍可保留为探索性评测数据。

## 安全边界

事件和 Artifact 禁止包含凭据、生产受限源码、无限量原始日志、任意文件系统路径或未脱敏敏感值。发现以下
内容时入口必须 fail closed，审计和日志不得回显正文：

- AK/SK、Token、Cookie、Authorization、Password、Secret 等字段或特征值；
- `bucket`、`objectKey`、`accessKey` 等绕过受控 Artifact locator 的存储字段；
- `topic`、`partition`、`offset`、`broker`、`consumerGroup` 等 broker 字段；
- 华为云 SDK Request/Response 类型名或供应商 DTO；
- Windows drive、UNC、反斜杠、`..` 路径穿越。

## 稳定错误码

`CONTRACT_VALIDATION_FAILED`、`UNSUPPORTED_SCHEMA`、`IDEMPOTENCY_CONFLICT`、`SEQUENCE_GAP`、
`ARTIFACT_INTEGRITY_FAILED`、`SECURITY_BOUNDARY_VIOLATION`、`PAYLOAD_TOO_LARGE`。

## 离线验证

在 DPOMAgent 仓库根目录执行：

```powershell
python contracts/diagnosis-event/v1/validate_contract.py
```

验证只读取本目录，不访问华为云、OBS、MySQL、Kafka、LLM 或网络。
