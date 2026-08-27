-- Every result set must be empty before enabling DPOMAgent authority writes.
SELECT required_table.table_name AS missing_table
FROM (
    SELECT 'authority_investigation_head' AS table_name
    UNION ALL SELECT 'authority_investigation_revision'
    UNION ALL SELECT 'authority_tool_use'
    UNION ALL SELECT 'authority_audit'
    UNION ALL SELECT 'authority_progress_intent'
    UNION ALL SELECT 'authority_progress_attempt'
    UNION ALL SELECT 'authority_diagnosis_source'
    UNION ALL SELECT 'authority_publication_intent'
    UNION ALL SELECT 'authority_publication_attempt'
    UNION ALL SELECT 'authority_diagnostic_report_revision'
    UNION ALL SELECT 'authority_diagnostic_report_head'
) required_table
LEFT JOIN information_schema.tables actual
    ON actual.table_schema = DATABASE()
    AND actual.table_name = required_table.table_name
WHERE actual.table_name IS NULL;

SELECT investigation_id, aggregate_version, steps_used, tool_calls_used, no_progress_rounds
FROM authority_investigation_head
WHERE aggregate_version < 0 OR steps_used < 0 OR tool_calls_used < 0 OR no_progress_rounds < 0;

SELECT investigation_id, aggregate_version, snapshot_sha256
FROM authority_investigation_head
WHERE snapshot_sha256 NOT REGEXP '^[0-9a-f]{64}$'
UNION ALL
SELECT investigation_id, aggregate_version, snapshot_sha256
FROM authority_investigation_revision
WHERE snapshot_sha256 NOT REGEXP '^[0-9a-f]{64}$';

SELECT investigation_id, aggregate_version, source_sha256, document_sha256
FROM authority_diagnosis_source
WHERE source_sha256 NOT REGEXP '^[0-9a-f]{64}$'
   OR document_sha256 NOT REGEXP '^[0-9a-f]{64}$';

SELECT head.investigation_id, head.aggregate_version
FROM authority_investigation_head head
LEFT JOIN authority_investigation_revision revision
    ON revision.investigation_id = head.investigation_id
    AND revision.aggregate_version = head.aggregate_version
WHERE revision.investigation_id IS NULL;

SELECT audit.investigation_id, MIN(audit.sequence_number) AS first_sequence,
       MAX(audit.sequence_number) AS last_sequence, COUNT(*) AS row_count
FROM authority_audit audit
GROUP BY audit.investigation_id
HAVING first_sequence <> 1 OR last_sequence <> row_count;

SELECT revision.report_id, revision.report_digest, revision.source_digest
FROM authority_diagnostic_report_revision revision
WHERE revision.revision_number <= 0
   OR revision.request_fingerprint NOT REGEXP '^[0-9a-f]{64}$'
   OR revision.report_digest NOT REGEXP '^[0-9a-f]{64}$'
   OR revision.source_digest NOT REGEXP '^[0-9a-f]{64}$';

SELECT head.investigation_id, head.latest_revision, revision.revision_number
FROM authority_diagnostic_report_head head
LEFT JOIN authority_diagnostic_report_revision revision ON revision.report_id=head.latest_report_id
WHERE revision.report_id IS NULL OR revision.revision_number <> head.latest_revision;

SELECT source.investigation_id, source.source_id
FROM authority_diagnosis_source source
LEFT JOIN authority_publication_intent intent ON intent.source_id = source.source_id
WHERE intent.intent_id IS NULL OR intent.status <> 'PENDING';

SELECT intent_id, canonical_sha256, topic_name, schema_version
FROM authority_publication_intent
WHERE canonical_sha256 NOT REGEXP '^[0-9a-f]{64}$'
   OR topic_name <> 'dpom.diagnosis-event.v2'
   OR schema_version NOT REGEXP '^2\\.[0-9]+$';

SELECT progress_id, progress_sequence, aggregate_version, canonical_sha256, topic_name, schema_version
FROM authority_progress_intent
WHERE progress_sequence <= 0 OR aggregate_version < 0
   OR canonical_sha256 NOT REGEXP '^[0-9a-f]{64}$'
   OR topic_name <> 'dpom.diagnosis-progress.v1'
   OR schema_version NOT REGEXP '^1\\.[0-9]+$';

SELECT investigation_id, MIN(progress_sequence) AS first_sequence,
       MAX(progress_sequence) AS last_sequence, COUNT(*) AS row_count
FROM authority_progress_intent
GROUP BY investigation_id
HAVING first_sequence <> 1 OR last_sequence <> row_count;
