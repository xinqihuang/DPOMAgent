# Phase 1B Kafka contracts

本目录只定义 transport envelope。canonical domain value 分别由 Diagnosis Event v2 与 Diagnosis Progress v1
JSON Schema 定义；Kafka 元数据不得改变身份、摘要、authority、ordering 或 SRE normalized ingestion outcome。

两个 topic 均默认关闭，生产启用前必须验证 broker TLS/auth、ACL、topic 配置、partition key、容量、schema readiness、
authority epoch 和 consumer quarantine readiness。任何凭据只从 secret manager 注入，不进入 header、value、日志或报告。
