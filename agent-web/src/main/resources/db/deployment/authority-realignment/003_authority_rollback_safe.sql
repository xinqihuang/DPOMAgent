-- Stop DPOMAgent writers and take a backup before this guarded rollback.
SET @authority_rows=(
    (SELECT COUNT(*) FROM authority_investigation_head)
    + (SELECT COUNT(*) FROM authority_investigation_revision)
    + (SELECT COUNT(*) FROM authority_tool_use)
    + (SELECT COUNT(*) FROM authority_audit)
    + (SELECT COUNT(*) FROM authority_diagnosis_source)
    + (SELECT COUNT(*) FROM authority_publication_intent)
);
SET @authority_refusal=IF(
    @authority_rows=0,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''AUTHORITY_HISTORY_PRESENT_ROLLBACK_REFUSED'''
);
PREPARE authority_guard FROM @authority_refusal;
EXECUTE authority_guard;
DEALLOCATE PREPARE authority_guard;

DROP TABLE authority_publication_intent;
DROP TABLE authority_diagnosis_source;
DROP TABLE authority_audit;
DROP TABLE authority_tool_use;
DROP TABLE authority_investigation_revision;
DROP TABLE authority_investigation_head;
