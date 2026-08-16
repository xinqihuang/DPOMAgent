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
