-- 日志到代码证据束：仅保存有界、脱敏的摘要 JSON，不保存海量原始日志。
-- 兼容 MySQL 8 与 H2(MySQL 模式)。

CREATE TABLE evidence_bundle (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    investigation_id BIGINT       NOT NULL,
    service_code     VARCHAR(128),
    commit_sha       VARCHAR(64),
    bundle_json      TEXT         NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_evidence_bundle_investigation ON evidence_bundle (investigation_id);
