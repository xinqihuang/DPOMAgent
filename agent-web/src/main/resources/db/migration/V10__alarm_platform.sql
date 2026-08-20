-- 告警中台 schema：告警、告警事件、事件成员。
-- DDL 同时兼容 MySQL 8 与 H2(MySQL 模式) 以便集成测试。

CREATE TABLE alarm (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    source            VARCHAR(16)  NOT NULL,
    ingestion_mode    VARCHAR(16)  NOT NULL,
    external_id       VARCHAR(255),
    fingerprint       VARCHAR(128) NOT NULL,
    resource_id       VARCHAR(255) NOT NULL,
    alarm_name        VARCHAR(255) NOT NULL,
    severity          VARCHAR(16)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    occurrence_count  INT          NOT NULL DEFAULT 1,
    first_occurred_at TIMESTAMP    NOT NULL,
    last_occurred_at  TIMESTAMP    NOT NULL,
    ingested_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    service_code      VARCHAR(128),
    environment       VARCHAR(64),
    raw_payload       TEXT,
    sample_payloads   TEXT
);

CREATE INDEX idx_alarm_fingerprint ON alarm (fingerprint, last_occurred_at);
CREATE INDEX idx_alarm_query ON alarm (severity, status, last_occurred_at);

CREATE TABLE alarm_incident (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    status                 VARCHAR(16)  NOT NULL,
    severity               VARCHAR(16)  NOT NULL,
    service_code           VARCHAR(128),
    environment            VARCHAR(64),
    correlation_basis      VARCHAR(64)  NOT NULL,
    summary                TEXT,
    started_at             TIMESTAMP    NOT NULL,
    ended_at               TIMESTAMP,
    escalation_candidate   BOOLEAN      NOT NULL DEFAULT FALSE,
    escalation_evaluated_at TIMESTAMP,
    assignee               VARCHAR(128),
    acknowledged_at        TIMESTAMP,
    resolved_at            TIMESTAMP,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_alarm_incident_status ON alarm_incident (status, started_at);

CREATE TABLE alarm_incident_member (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT      NOT NULL,
    alarm_id    BIGINT      NOT NULL,
    added_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_incident_member_incident ON alarm_incident_member (incident_id);
CREATE INDEX idx_incident_member_alarm ON alarm_incident_member (alarm_id);
