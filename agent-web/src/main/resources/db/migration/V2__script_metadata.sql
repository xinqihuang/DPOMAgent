-- 脚本工件补充诊断元数据列
ALTER TABLE script_artifact ADD COLUMN hypotheses_to_validate TEXT;
ALTER TABLE script_artifact ADD COLUMN expected_output TEXT;
ALTER TABLE script_artifact ADD COLUMN instructions TEXT;
