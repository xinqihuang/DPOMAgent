# Diagnosis Progress v1

Progress 是 DPOMBase 已持久化 progress log 的有界投影，同时供直接 SSE 与 Kafka publisher 使用。

- `progressSequence` 在 investigation 内单调递增；SSE `Last-Event-ID` 使用该值恢复。
- canonical record 最大 8 KiB，topic key 固定为 `investigationId`。
- 只允许稳定 status/stage/summaryCode、百分比和 checkpoint reference；禁止证据正文、Prompt、原始模型输出、
  凭据、任意异常文本和 transport metadata。
- retention 短于 Portal 请求的 replay window 时，SSE 返回明确 resynchronization 响应，由 Portal 拉取权威 REST snapshot。
