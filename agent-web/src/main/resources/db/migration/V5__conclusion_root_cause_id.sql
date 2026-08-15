-- 结论增加稳定根因标识（如 AssetRepository.insert），与自然语言 root_cause 分离。
-- 兼容 MySQL 8 与 H2(MySQL 模式)。

ALTER TABLE conclusion ADD COLUMN root_cause_id VARCHAR(512);
