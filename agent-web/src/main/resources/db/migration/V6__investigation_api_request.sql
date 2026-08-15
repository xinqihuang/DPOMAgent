-- 调查 API 幂等与执行元数据。兼容 MySQL 8 与 H2(MySQL 模式)。
CREATE TABLE investigation_api_request (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key  VARCHAR(128) NOT NULL,
    payload_hash     VARCHAR(64)  NOT NULL,
    investigation_id BIGINT       NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX idx_api_request_key ON investigation_api_request (idempotency_key);
