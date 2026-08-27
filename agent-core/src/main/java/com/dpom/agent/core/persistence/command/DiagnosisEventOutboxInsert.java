package com.dpom.agent.core.persistence.command;

import java.time.LocalDateTime;

/**
 * Diagnosis Event 发件箱插入命令。
 */
public class DiagnosisEventOutboxInsert {

    private Long id;
    private final String eventId;
    private final String idempotencyKey;
    private final long investigationId;
    private final long runId;
    private final String eventType;
    private final long aggregateSequence;
    private final String schemaVersion;
    private final String canonicalContent;
    private final String canonicalSha256;
    private final LocalDateTime nextAttemptAt;

    /** 创建不可变内容的待投递记录。 */
    public DiagnosisEventOutboxInsert(String eventId, String idempotencyKey, long investigationId, long runId,
                                      String eventType, long aggregateSequence, String schemaVersion,
                                      String canonicalContent, String canonicalSha256, LocalDateTime nextAttemptAt) {
        this.eventId = eventId;
        this.idempotencyKey = idempotencyKey;
        this.investigationId = investigationId;
        this.runId = runId;
        this.eventType = eventType;
        this.aggregateSequence = aggregateSequence;
        this.schemaVersion = schemaVersion;
        this.canonicalContent = canonicalContent;
        this.canonicalSha256 = canonicalSha256;
        this.nextAttemptAt = nextAttemptAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getInvestigationId() { return investigationId; }
    public long getRunId() { return runId; }
    public String getEventType() { return eventType; }
    public long getAggregateSequence() { return aggregateSequence; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getCanonicalContent() { return canonicalContent; }
    public String getCanonicalSha256() { return canonicalSha256; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
}
