-- 调查 API 执行元数据：started_at/completed_at/last_error_code。兼容 MySQL 8 与 H2(MySQL 模式)。
ALTER TABLE investigation_api_request ADD COLUMN started_at TIMESTAMP;
ALTER TABLE investigation_api_request ADD COLUMN completed_at TIMESTAMP;
ALTER TABLE investigation_api_request ADD COLUMN last_error_code VARCHAR(64);
