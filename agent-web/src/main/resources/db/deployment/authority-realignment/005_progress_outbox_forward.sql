-- Existing authority-realignment deployments: add DPOMAgent-owned Diagnosis Progress v1 Outbox.
-- Stop authority writers while applying so every authority_audit row can be backfilled transactionally.
CREATE TABLE authority_progress_intent (
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
    CONSTRAINT fk_authority_progress_audit FOREIGN KEY (audit_id) REFERENCES authority_audit (audit_id),
    CONSTRAINT fk_authority_progress_head FOREIGN KEY (investigation_id)
        REFERENCES authority_investigation_head (investigation_id),
    CONSTRAINT chk_authority_progress_numbers CHECK (
        progress_sequence > 0 AND aggregate_version >= 0 AND attempt_count >= 0
    ),
    CONSTRAINT chk_authority_progress_digest CHECK (canonical_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE authority_progress_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    progress_id VARCHAR(36) NOT NULL,
    attempt_number INT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_authority_progress_attempt FOREIGN KEY (progress_id)
        REFERENCES authority_progress_intent (progress_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Existing Investigations are intentionally not backfilled: they remain queryable through authenticated SSE/REST.
-- Only Investigations created after this table exists receive sequence 1 and become Kafka Progress admitted.
-- Admission MUST remain disabled if this query returns any row.
SELECT investigation_id, MIN(progress_sequence), MAX(progress_sequence), COUNT(*)
FROM authority_progress_intent
GROUP BY investigation_id
HAVING MIN(progress_sequence) <> 1 OR MAX(progress_sequence) <> COUNT(*);
