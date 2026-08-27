CREATE TABLE IF NOT EXISTS authority_investigation_head (
    investigation_id VARCHAR(128) PRIMARY KEY,
    incident_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_run_id VARCHAR(128),
    steps_used INT NOT NULL,
    tool_calls_used INT NOT NULL,
    no_progress_rounds INT NOT NULL,
    snapshot_json CLOB NOT NULL,
    snapshot_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS authority_investigation_revision (
    investigation_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    snapshot_json CLOB NOT NULL,
    snapshot_sha256 CHAR(64) NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (investigation_id, aggregate_version),
    FOREIGN KEY (investigation_id) REFERENCES authority_investigation_head (investigation_id)
);

CREATE TABLE IF NOT EXISTS authority_tool_use (
    tool_use_id VARCHAR(128) PRIMARY KEY,
    investigation_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    contract_version VARCHAR(128) NOT NULL,
    argument_sha256 CHAR(64) NOT NULL,
    argument_names_json CLOB NOT NULL,
    argument_size_bytes INT NOT NULL,
    target_scope VARCHAR(256) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64),
    evidence_references_json CLOB NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    UNIQUE (investigation_id, correlation_id),
    FOREIGN KEY (investigation_id) REFERENCES authority_investigation_head (investigation_id)
);

CREATE TABLE IF NOT EXISTS authority_audit (
    audit_id VARCHAR(128) PRIMARY KEY,
    investigation_id VARCHAR(128) NOT NULL,
    sequence_number BIGINT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    audit_kind VARCHAR(64) NOT NULL,
    entity_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    UNIQUE (investigation_id, sequence_number),
    FOREIGN KEY (investigation_id) REFERENCES authority_investigation_head (investigation_id)
);

CREATE TABLE IF NOT EXISTS authority_diagnosis_source (
    source_id VARCHAR(128) PRIMARY KEY,
    investigation_id VARCHAR(128) NOT NULL UNIQUE,
    aggregate_version BIGINT NOT NULL,
    contract_version VARCHAR(64) NOT NULL,
    source_json CLOB NOT NULL,
    source_sha256 CHAR(64) NOT NULL,
    document_sha256 CHAR(64) NOT NULL,
    committed_at TIMESTAMP(6) NOT NULL,
    UNIQUE (investigation_id, aggregate_version),
    FOREIGN KEY (investigation_id) REFERENCES authority_investigation_head (investigation_id)
);

CREATE TABLE IF NOT EXISTS authority_publication_intent (
    intent_id VARCHAR(128) PRIMARY KEY,
    investigation_id VARCHAR(128) NOT NULL,
    aggregate_sequence BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    source_sha256 CHAR(64) NOT NULL,
    topic_name VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    schema_version VARCHAR(16) NOT NULL,
    canonical_content CLOB NOT NULL,
    canonical_sha256 CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    eligible_at TIMESTAMP(6) NOT NULL,
    lease_expires_at TIMESTAMP(6),
    lease_owner VARCHAR(128),
    lease_token VARCHAR(64),
    last_error_code VARCHAR(64),
    delivered_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE (source_id, event_type),
    FOREIGN KEY (investigation_id) REFERENCES authority_investigation_head (investigation_id),
    FOREIGN KEY (source_id) REFERENCES authority_diagnosis_source (source_id)
);

CREATE TABLE IF NOT EXISTS authority_publication_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    intent_id VARCHAR(128) NOT NULL,
    attempt_number INT NOT NULL,
    transport VARCHAR(16) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (intent_id) REFERENCES authority_publication_intent (intent_id)
);
