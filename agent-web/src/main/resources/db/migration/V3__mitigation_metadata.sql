-- 修复脚本补充根因/证据/目标元数据列
ALTER TABLE script_artifact ADD COLUMN root_cause TEXT;
ALTER TABLE script_artifact ADD COLUMN evidence_ids TEXT;
ALTER TABLE script_artifact ADD COLUMN target TEXT;
