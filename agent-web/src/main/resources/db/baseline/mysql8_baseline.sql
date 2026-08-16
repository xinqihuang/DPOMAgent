-- MySQL 8.0 clean-install baseline（基线缺陷修正版）。
-- 说明：已发布的 V8__evidence_handoff.sql 在 handoff_import 表使用 MySQL 8.0 保留字 release/commit 作裸列名，
--       在真实 MySQL 8.0 上无法执行（历史基线缺陷），故 V8 保持不可变，由本 baseline 提供等价且修正后的完整 schema。
-- 用途：全新 RDS for MySQL 安装时，先执行本文件创建全量 schema，再以 Flyway baseline-on-migrate + baseline-version=9 启动。
-- 本文件不参与 Flyway 版本迁移（不在 flyway.locations 下），仅作 clean-install baseline 资源。

-- ===== V1__init_schema.sql =====

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

-- ===== V2__script_metadata.sql =====

-- 脚本工件补充诊断元数据列
ALTER TABLE script_artifact ADD COLUMN hypotheses_to_validate TEXT;
ALTER TABLE script_artifact ADD COLUMN expected_output TEXT;
ALTER TABLE script_artifact ADD COLUMN instructions TEXT;

-- ===== V3__mitigation_metadata.sql =====

-- 修复脚本补充根因/证据/目标元数据列
ALTER TABLE script_artifact ADD COLUMN root_cause TEXT;
ALTER TABLE script_artifact ADD COLUMN evidence_ids TEXT;
ALTER TABLE script_artifact ADD COLUMN target TEXT;

-- ===== V4__evidence_bundle.sql =====

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

-- ===== V5__conclusion_root_cause_id.sql =====

-- 结论增加稳定根因标识（如 AssetRepository.insert），与自然语言 root_cause 分离。
-- 兼容 MySQL 8 与 H2(MySQL 模式)。

ALTER TABLE conclusion ADD COLUMN root_cause_id VARCHAR(512);

-- ===== V6__investigation_api_request.sql =====

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

-- ===== V7__api_request_execution_metadata.sql =====

-- 调查 API 执行元数据：started_at/completed_at/last_error_code。兼容 MySQL 8 与 H2(MySQL 模式)。
ALTER TABLE investigation_api_request ADD COLUMN started_at TIMESTAMP;
ALTER TABLE investigation_api_request ADD COLUMN completed_at TIMESTAMP;
ALTER TABLE investigation_api_request ADD COLUMN last_error_code VARCHAR(64);

-- ===== V8__evidence_handoff.sql =====

-- 证据交接：升级判定、上传批准与研发侧导入的追加式审计记录。兼容 MySQL 8 与 H2(MySQL 模式)。

CREATE TABLE escalation_decision (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    investigation_id BIGINT       NOT NULL,
    eligible         BOOLEAN      NOT NULL,
    reasons          VARCHAR(512) NOT NULL,
    missing_evidence VARCHAR(1024),
    confidence       INT          NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_escalation_investigation ON escalation_decision (investigation_id);

CREATE TABLE handoff_upload (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    investigation_id BIGINT       NOT NULL,
    package_id       VARCHAR(64)  NOT NULL,
    object_key       VARCHAR(255),
    schema_version   INT          NOT NULL,
    checksum         VARCHAR(64),
    size_bytes       BIGINT       NOT NULL,
    approval_status  VARCHAR(32)  NOT NULL,
    approved_at      TIMESTAMP,
    uploaded_at      TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_handoff_package ON handoff_upload (package_id);
CREATE INDEX idx_handoff_investigation ON handoff_upload (investigation_id);

CREATE TABLE handoff_import (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    package_id VARCHAR(64)  NOT NULL,
    service    VARCHAR(128),
    `release`  VARCHAR(128),
    `commit`   VARCHAR(64),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_import_package ON handoff_import (package_id);

-- ===== V9__handoff_approval_audit.sql =====

-- 证据交接：审批引用/理由/过期 + 追加式审计。兼容 MySQL 8 与 H2(MySQL 模式)。

ALTER TABLE handoff_upload ADD COLUMN approver_ref VARCHAR(128);
ALTER TABLE handoff_upload ADD COLUMN approval_reason VARCHAR(512);
ALTER TABLE handoff_upload ADD COLUMN approval_expires_at TIMESTAMP;

CREATE TABLE handoff_audit (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type       VARCHAR(64)  NOT NULL,
    result           VARCHAR(16)  NOT NULL,
    error_code       VARCHAR(64),
    investigation_id BIGINT,
    package_id       VARCHAR(64),
    correlation_id   VARCHAR(64),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_handoff_audit_investigation ON handoff_audit (investigation_id);
CREATE INDEX idx_handoff_audit_package ON handoff_audit (package_id);