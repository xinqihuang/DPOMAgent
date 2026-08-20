-- 告警中台 schema：通知规则、通知记录、抑制/静默、审计。
-- DDL 同时兼容 MySQL 8 与 H2(MySQL 模式)。

CREATE TABLE notification_rule (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    source_filter       VARCHAR(16),
    service_code_filter VARCHAR(128),
    resource_filter     VARCHAR(255),
    severity_filter     VARCHAR(16),
    tag_filter          VARCHAR(255),
    channels            TEXT         NOT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification_record (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id  BIGINT       NOT NULL,
    rule_id      BIGINT,
    channel      VARCHAR(32)  NOT NULL,
    recipient    VARCHAR(512),
    status       VARCHAR(16)  NOT NULL,
    error_message TEXT,
    sent_at      TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notif_record_incident ON notification_record (incident_id);

CREATE TABLE alarm_suppression (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    kind       VARCHAR(16)  NOT NULL,
    match_key  VARCHAR(255) NOT NULL,
    reason     VARCHAR(255),
    start_at   TIMESTAMP    NOT NULL,
    end_at     TIMESTAMP    NOT NULL,
    created_by VARCHAR(128),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_suppression_match ON alarm_suppression (match_key, start_at, end_at);

CREATE TABLE alarm_audit (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    action      VARCHAR(32) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id   BIGINT,
    operator    VARCHAR(128),
    detail      TEXT,
    result      VARCHAR(32),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_target ON alarm_audit (target_type, target_id, created_at);
