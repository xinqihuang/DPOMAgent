-- Add immutable diagnosis-only report revisions after the Phase 1 authority tables exist.
CREATE TABLE IF NOT EXISTS authority_diagnostic_report_revision (
    report_id VARCHAR(128) NOT NULL PRIMARY KEY,
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
    UNIQUE KEY uk_authority_report_request (investigation_id, request_idempotency_key),
    UNIQUE KEY uk_authority_report_revision (investigation_id, revision_number),
    CONSTRAINT fk_authority_report_investigation FOREIGN KEY (investigation_id) REFERENCES authority_investigation_head (investigation_id),
    CONSTRAINT fk_authority_report_source FOREIGN KEY (diagnosis_source_id) REFERENCES authority_diagnosis_source (source_id),
    CONSTRAINT fk_authority_report_parent FOREIGN KEY (supersedes_report_id) REFERENCES authority_diagnostic_report_revision (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS authority_diagnostic_report_head (
    investigation_id VARCHAR(128) NOT NULL PRIMARY KEY,
    latest_report_id VARCHAR(128) NOT NULL,
    latest_revision BIGINT NOT NULL,
    lock_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_authority_report_head_investigation FOREIGN KEY (investigation_id) REFERENCES authority_investigation_head (investigation_id),
    CONSTRAINT fk_authority_report_head_latest FOREIGN KEY (latest_report_id) REFERENCES authority_diagnostic_report_revision (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
