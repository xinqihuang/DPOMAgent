-- DPOMAgent Investigation 最小可恢复持久化 schema
-- 注意：DDL 需同时兼容 MySQL 8 与 H2(MySQL 模式) 以便集成测试。

CREATE TABLE incident (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_code    VARCHAR(128) NOT NULL,
    environment     VARCHAR(64)  NOT NULL,
    release_version VARCHAR(128),
    commit_sha      VARCHAR(64),
    symptom         TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE investigation (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id             BIGINT       NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    current_run_id          BIGINT,
    max_steps               INT          NOT NULL DEFAULT 50,
    max_tool_calls          INT          NOT NULL DEFAULT 100,
    max_duration_seconds    INT          NOT NULL DEFAULT 1800,
    max_no_progress_rounds  INT          NOT NULL DEFAULT 5,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_investigation_incident ON investigation (incident_id);

CREATE TABLE investigation_run (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    investigation_id BIGINT       NOT NULL,
    model_version    VARCHAR(128),
    prompt_version   VARCHAR(128),
    toolset_version  VARCHAR(128),
    started_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at         TIMESTAMP
);

CREATE INDEX idx_run_investigation ON investigation_run (investigation_id);

-- Step 仅追加：无更新、无删除。
CREATE TABLE investigation_step (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    investigation_id BIGINT       NOT NULL,
    run_id           BIGINT,
    step_order       INT          NOT NULL,
    step_type        VARCHAR(32)  NOT NULL,
    summary          TEXT,
    payload_json     TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_step_investigation ON investigation_step (investigation_id, step_order);

CREATE TABLE observation (
    id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
    investigation_id           BIGINT       NOT NULL,
    run_id                     BIGINT,
    source                     VARCHAR(64)  NOT NULL,
    artifact_ref               VARCHAR(255),
    location                   VARCHAR(512),
    supports_hypothesis_ids    VARCHAR(512),
    contradicts_hypothesis_ids VARCHAR(512),
    summary                    TEXT,
    payload_json               TEXT,
    created_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_observation_investigation ON observation (investigation_id);

CREATE TABLE hypothesis (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    investigation_id BIGINT       NOT NULL,
    parent_id        BIGINT,
    description      TEXT         NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    missing_checks   TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_hypothesis_investigation ON hypothesis (investigation_id);

CREATE TABLE conclusion (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    investigation_id    BIGINT       NOT NULL,
    result_type         VARCHAR(32)  NOT NULL,
    root_cause          TEXT,
    evidence_ids        VARCHAR(1024),
    unresolved_questions TEXT,
    summary             TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conclusion_investigation ON conclusion (investigation_id);

CREATE TABLE script_artifact (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    investigation_id BIGINT       NOT NULL,
    script_type      VARCHAR(32)  NOT NULL,
    language         VARCHAR(32)  NOT NULL,
    purpose          TEXT,
    risk_level       VARCHAR(32),
    read_only        BOOLEAN      NOT NULL DEFAULT TRUE,
    approval_status  VARCHAR(32)  NOT NULL,
    preconditions    TEXT,
    verification     TEXT,
    rollback         TEXT,
    content          TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_script_investigation ON script_artifact (investigation_id);

-- ToolCall 仅追加审计。
CREATE TABLE tool_call_audit (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    investigation_id     BIGINT       NOT NULL,
    run_id               BIGINT,
    tool_name            VARCHAR(128) NOT NULL,
    tool_input           TEXT,
    tool_output_summary  TEXT,
    duration_ms          BIGINT,
    success              BOOLEAN,
    error_message        TEXT,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_investigation ON tool_call_audit (investigation_id);
