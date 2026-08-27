-- Diagnosis Event v1：事务发件箱、追加式审计与持久化重放 nonce。
-- DDL 同时兼容 MySQL 8 与 H2（MySQL 模式）。

CREATE TABLE diagnosis_event_outbox (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id             VARCHAR(36)  NOT NULL,
    idempotency_key      VARCHAR(200) NOT NULL,
    investigation_id     BIGINT       NOT NULL,
    run_id               BIGINT       NOT NULL,
    event_type           VARCHAR(64)  NOT NULL,
    aggregate_sequence   BIGINT       NOT NULL,
    schema_version       VARCHAR(16)  NOT NULL,
    canonical_content    MEDIUMTEXT   NOT NULL,
    canonical_sha256     CHAR(64)     NOT NULL,
    status               VARCHAR(16)  NOT NULL,
    attempt_count        INT          NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMP    NOT NULL,
    lease_owner          VARCHAR(128),
    lease_token          VARCHAR(36),
    lease_expires_at     TIMESTAMP,
    last_error_code      VARCHAR(64),
    delivered_at         TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_diagnosis_outbox_event_id
    ON diagnosis_event_outbox (event_id);
CREATE UNIQUE INDEX uk_diagnosis_outbox_idempotency
    ON diagnosis_event_outbox (idempotency_key);
CREATE UNIQUE INDEX uk_diagnosis_outbox_terminal
    ON diagnosis_event_outbox (investigation_id, run_id, event_type);
CREATE INDEX idx_diagnosis_outbox_ready
    ON diagnosis_event_outbox (status, next_attempt_at, id);
CREATE INDEX idx_diagnosis_outbox_expired_lease
    ON diagnosis_event_outbox (status, lease_expires_at, id);

CREATE TABLE diagnosis_event_audit (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id       VARCHAR(36)  NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    action         VARCHAR(32)  NOT NULL,
    result         VARCHAR(32)  NOT NULL,
    error_code     VARCHAR(64),
    operator_ref   VARCHAR(128),
    reason         VARCHAR(512),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_diagnosis_audit_event
    ON diagnosis_event_audit (event_id, created_at, id);

CREATE TABLE diagnosis_event_replay_nonce (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    nonce      VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_diagnosis_replay_nonce
    ON diagnosis_event_replay_nonce (nonce);
CREATE INDEX idx_diagnosis_replay_nonce_expiry
    ON diagnosis_event_replay_nonce (expires_at);
