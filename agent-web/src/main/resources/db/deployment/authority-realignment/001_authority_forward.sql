-- Operator-reviewed MySQL 8 deployment. Never run from application Flyway.
CREATE TABLE IF NOT EXISTS authority_investigation_head (
    investigation_id VARCHAR(128) NOT NULL,
    incident_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_run_id VARCHAR(128) NULL,
    steps_used INT NOT NULL,
    tool_calls_used INT NOT NULL,
    no_progress_rounds INT NOT NULL,
    snapshot_json MEDIUMTEXT NOT NULL,
    snapshot_sha256 CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (investigation_id),
    KEY idx_authority_head_incident (incident_id),
    KEY idx_authority_head_status_updated (status, updated_at),
    CONSTRAINT chk_authority_head_version CHECK (aggregate_version >= 0),
    CONSTRAINT chk_authority_head_counters CHECK (
        steps_used >= 0 AND tool_calls_used >= 0 AND no_progress_rounds >= 0
    ),
    CONSTRAINT chk_authority_head_digest CHECK (
        snapshot_sha256 REGEXP '^[0-9a-f]{64}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS authority_investigation_revision (
    investigation_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    snapshot_json MEDIUMTEXT NOT NULL,
    snapshot_sha256 CHAR(64) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (investigation_id, aggregate_version),
    KEY idx_authority_revision_recorded (recorded_at),
    CONSTRAINT fk_authority_revision_head FOREIGN KEY (investigation_id)
        REFERENCES authority_investigation_head (investigation_id),
    CONSTRAINT chk_authority_revision_version CHECK (aggregate_version >= 0),
    CONSTRAINT chk_authority_revision_digest CHECK (
        snapshot_sha256 REGEXP '^[0-9a-f]{64}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS authority_tool_use (
    tool_use_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    contract_version VARCHAR(128) NOT NULL,
    argument_sha256 CHAR(64) NOT NULL,
    argument_names_json TEXT NOT NULL,
    argument_size_bytes INT NOT NULL,
    target_scope VARCHAR(256) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NULL,
    evidence_references_json TEXT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tool_use_id),
    UNIQUE KEY uk_authority_tool_correlation (investigation_id, correlation_id),
    KEY idx_authority_tool_investigation_time (investigation_id, occurred_at),
    CONSTRAINT fk_authority_tool_head FOREIGN KEY (investigation_id)
        REFERENCES authority_investigation_head (investigation_id),
    CONSTRAINT chk_authority_tool_digest CHECK (
        argument_sha256 REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_authority_tool_argument_size CHECK (
        argument_size_bytes >= 0 AND argument_size_bytes <= 65536
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS authority_audit (
    audit_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    sequence_number BIGINT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    audit_kind VARCHAR(64) NOT NULL,
    entity_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (audit_id),
    UNIQUE KEY uk_authority_audit_sequence (investigation_id, sequence_number),
    KEY idx_authority_audit_version (investigation_id, aggregate_version),
    CONSTRAINT fk_authority_audit_head FOREIGN KEY (investigation_id)
        REFERENCES authority_investigation_head (investigation_id),
    CONSTRAINT chk_authority_audit_numbers CHECK (
        sequence_number > 0 AND aggregate_version >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS authority_progress_intent (
    progress_id VARCHAR(36) NOT NULL,
    audit_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(128) NULL,
    progress_sequence BIGINT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    authority_epoch VARCHAR(128) NOT NULL,
    topic_name VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    schema_version VARCHAR(16) NOT NULL,
    canonical_content TEXT NOT NULL,
    canonical_sha256 CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    eligible_at DATETIME(6) NOT NULL,
    lease_expires_at DATETIME(6) NULL,
    lease_owner VARCHAR(128) NULL,
    lease_token VARCHAR(64) NULL,
    last_error_code VARCHAR(64) NULL,
    delivered_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (progress_id),
    UNIQUE KEY uk_authority_progress_audit (audit_id),
    UNIQUE KEY uk_authority_progress_sequence (investigation_id, progress_sequence),
    UNIQUE KEY uk_authority_progress_idempotency (idempotency_key),
    KEY idx_authority_progress_ready (status, eligible_at),
    CONSTRAINT fk_authority_progress_audit FOREIGN KEY (audit_id)
        REFERENCES authority_audit (audit_id),
    CONSTRAINT fk_authority_progress_head FOREIGN KEY (investigation_id)
        REFERENCES authority_investigation_head (investigation_id),
    CONSTRAINT chk_authority_progress_numbers CHECK (
        progress_sequence > 0 AND aggregate_version >= 0 AND attempt_count >= 0
    ),
    CONSTRAINT chk_authority_progress_digest CHECK (
        canonical_sha256 REGEXP '^[0-9a-f]{64}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS authority_progress_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    progress_id VARCHAR(36) NOT NULL,
    attempt_number INT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_authority_progress_attempt FOREIGN KEY (progress_id)
        REFERENCES authority_progress_intent (progress_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS authority_diagnosis_source (
    source_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    contract_version VARCHAR(64) NOT NULL,
    source_json MEDIUMTEXT NOT NULL,
    source_sha256 CHAR(64) NOT NULL,
    document_sha256 CHAR(64) NOT NULL,
    committed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (source_id),
    UNIQUE KEY uk_authority_source_investigation (investigation_id),
    UNIQUE KEY uk_authority_source_version (investigation_id, aggregate_version),
    CONSTRAINT fk_authority_source_head FOREIGN KEY (investigation_id)
        REFERENCES authority_investigation_head (investigation_id),
    CONSTRAINT chk_authority_source_digest CHECK (
        source_sha256 REGEXP '^[0-9a-f]{64}$'
        AND document_sha256 REGEXP '^[0-9a-f]{64}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS authority_publication_intent (
    intent_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    aggregate_sequence BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    source_sha256 CHAR(64) NOT NULL,
    topic_name VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    schema_version VARCHAR(16) NOT NULL,
    canonical_content MEDIUMTEXT NOT NULL,
    canonical_sha256 CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    eligible_at DATETIME(6) NOT NULL,
    lease_expires_at DATETIME(6) NULL,
    lease_owner VARCHAR(128) NULL,
    lease_token VARCHAR(64) NULL,
    last_error_code VARCHAR(64) NULL,
    delivered_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (intent_id),
    UNIQUE KEY uk_authority_intent_source_event (source_id, event_type),
    UNIQUE KEY uk_authority_intent_idempotency (idempotency_key),
    KEY idx_authority_intent_ready (status, eligible_at),
    CONSTRAINT fk_authority_intent_head FOREIGN KEY (investigation_id)
        REFERENCES authority_investigation_head (investigation_id),
    CONSTRAINT fk_authority_intent_source FOREIGN KEY (source_id)
        REFERENCES authority_diagnosis_source (source_id),
    CONSTRAINT chk_authority_intent_sequence CHECK (aggregate_sequence >= 0),
    CONSTRAINT chk_authority_intent_digest CHECK (
        source_sha256 REGEXP '^[0-9a-f]{64}$'
        AND canonical_sha256 REGEXP '^[0-9a-f]{64}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS authority_publication_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    intent_id VARCHAR(128) NOT NULL,
    attempt_number INT NOT NULL,
    transport VARCHAR(16) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_authority_attempt_intent FOREIGN KEY (intent_id)
        REFERENCES authority_publication_intent (intent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS authority_diagnostic_report_revision (
    report_id VARCHAR(128) NOT NULL,
    investigation_id VARCHAR(128) NOT NULL,
    diagnosis_source_id VARCHAR(128) NOT NULL,
    request_idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    revision_number BIGINT NOT NULL,
    supersedes_report_id VARCHAR(128) NULL,
    change_reasons_json VARCHAR(2048) NOT NULL,
    canonical_content MEDIUMTEXT NOT NULL,
    report_digest CHAR(64) NOT NULL,
    source_digest CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (report_id),
    UNIQUE KEY uk_authority_report_request (investigation_id, request_idempotency_key),
    UNIQUE KEY uk_authority_report_revision (investigation_id, revision_number),
    CONSTRAINT fk_authority_report_investigation FOREIGN KEY (investigation_id)
        REFERENCES authority_investigation_head (investigation_id),
    CONSTRAINT fk_authority_report_source FOREIGN KEY (diagnosis_source_id)
        REFERENCES authority_diagnosis_source (source_id),
    CONSTRAINT fk_authority_report_parent FOREIGN KEY (supersedes_report_id)
        REFERENCES authority_diagnostic_report_revision (report_id),
    CONSTRAINT chk_authority_report_digest CHECK (
        request_fingerprint REGEXP '^[0-9a-f]{64}$'
        AND report_digest REGEXP '^[0-9a-f]{64}$'
        AND source_digest REGEXP '^[0-9a-f]{64}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS authority_diagnostic_report_head (
    investigation_id VARCHAR(128) NOT NULL,
    latest_report_id VARCHAR(128) NOT NULL,
    latest_revision BIGINT NOT NULL,
    lock_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (investigation_id),
    CONSTRAINT fk_authority_report_head_investigation FOREIGN KEY (investigation_id)
        REFERENCES authority_investigation_head (investigation_id),
    CONSTRAINT fk_authority_report_head_latest FOREIGN KEY (latest_report_id)
        REFERENCES authority_diagnostic_report_revision (report_id),
    CONSTRAINT chk_authority_report_head_version CHECK (latest_revision > 0 AND lock_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
