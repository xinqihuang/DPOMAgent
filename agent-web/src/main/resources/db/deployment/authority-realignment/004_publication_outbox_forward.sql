-- Existing authority-realignment deployments: freeze transport-neutral Diagnosis Event v2 content.
ALTER TABLE authority_publication_intent
    ADD COLUMN topic_name VARCHAR(128) NULL AFTER source_sha256,
    ADD COLUMN idempotency_key VARCHAR(200) NULL AFTER topic_name,
    ADD COLUMN schema_version VARCHAR(16) NULL AFTER idempotency_key,
    ADD COLUMN canonical_content MEDIUMTEXT NULL AFTER schema_version,
    ADD COLUMN canonical_sha256 CHAR(64) NULL AFTER canonical_content,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN lease_expires_at DATETIME(6) NULL AFTER eligible_at,
    ADD COLUMN lease_owner VARCHAR(128) NULL AFTER lease_expires_at,
    ADD COLUMN lease_token VARCHAR(64) NULL AFTER lease_owner,
    ADD COLUMN last_error_code VARCHAR(64) NULL AFTER lease_token,
    ADD COLUMN delivered_at DATETIME(6) NULL AFTER last_error_code,
    ADD COLUMN updated_at DATETIME(6) NULL AFTER created_at;

-- Admission remains default-off while legacy rows are reconciled. Only then make frozen columns mandatory.
-- Operators MUST verify no NULL row remains before executing these statements.
UPDATE authority_publication_intent SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE authority_publication_intent
    MODIFY topic_name VARCHAR(128) NOT NULL,
    MODIFY idempotency_key VARCHAR(200) NOT NULL,
    MODIFY schema_version VARCHAR(16) NOT NULL,
    MODIFY canonical_content MEDIUMTEXT NOT NULL,
    MODIFY canonical_sha256 CHAR(64) NOT NULL,
    MODIFY updated_at DATETIME(6) NOT NULL,
    ADD UNIQUE KEY uk_authority_intent_idempotency (idempotency_key);

CREATE TABLE authority_publication_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    intent_id VARCHAR(128) NOT NULL,
    attempt_number INT NOT NULL,
    transport VARCHAR(16) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_authority_attempt_intent FOREIGN KEY (intent_id)
        REFERENCES authority_publication_intent (intent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
