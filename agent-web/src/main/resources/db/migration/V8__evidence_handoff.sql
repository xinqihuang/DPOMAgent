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
    release    VARCHAR(128),
    commit     VARCHAR(64),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_import_package ON handoff_import (package_id);
