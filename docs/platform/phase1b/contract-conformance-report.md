# Phase 1B 契约与传输一致性报告

记录时间：2026-08-25（Asia/Shanghai）

## 发布资产

- Diagnosis Event v2：schema、source manifest、RFC 8785/SHA-256 规则、2 个 canonical positive fixtures、
  12 个稳定错误反例；Diagnosis Event v1 未修改。
- Evidence Manifest v1：有界证据引用、敏感级别、retention、integrity、1 个正例和 7 个安全/完整性/大小反例。
- Diagnosis Progress v1：持久化进度投影、authority/sequence、1 个正例和 6 个安全/大小反例。
- Kafka：`dpom.diagnosis-event.v2` 与 `dpom.diagnosis-progress.v1` 的 key、headers、bounds、兼容、
  retry、durable quarantine、ACL 和 retention 规则。

## 验证证据

- Python offline validator：`PHASE1B_CONTRACTS=PASS event=2/12 evidence=1/7 progress=1/6`。
- 同一份共享 Java conformance source 接入 DPOMBase `agentic-common` 和 SRE `sre-web` 默认测试源；
  两侧各 4 tests 通过。
- Java 使用独立 RFC 8785 实现复算 manifest 的 exact byte size 与 SHA-256，接受全部正例并按声明错误码
  拒绝全部反例。
- Kafka 与 HTTP test envelope 携带不同 signature/topic/partition/offset/retry metadata，归一化后的 identity、
  digest、investigation、sequence、authority epoch 和 `ACCEPTABLE` outcome 完全一致；transport metadata 不进入 canonical bytes。

这些测试只读取 `D:\code\contracts`，不需要 broker、数据库、云、LLM 或生产凭据。
