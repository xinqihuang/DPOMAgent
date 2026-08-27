# Evidence Manifest v1

该契约只描述有界证据的身份、类型、来源引用、时间、大小、摘要、脱敏状态、敏感级别和保留策略；
`contentIncluded` 必须为 `false`，不得包含证据正文、Prompt、原始模型输出、凭据、bucket/object key 或文件路径。

Manifest 最大 1 MiB、最多 256 项；每项最大 10 MiB，声明总大小最大 50 MiB。消费者在取用 Artifact 前必须
验证 media type、byteSize 与 SHA-256；不匹配时使用 `ARTIFACT_INTEGRITY_FAILED` 并失败关闭。
