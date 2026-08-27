package com.dpom.agent.core.persistence.command;

/**
 * Diagnosis Event 追加式审计插入命令。
 */
public class DiagnosisEventAuditInsert {

    private Long id;
    private final String eventId;
    private final String eventType;
    private final String action;
    private final String result;
    private final String errorCode;
    private final String operatorRef;
    private final String reason;
    private final String correlationId;

    /** 创建审计命令。 */
    public DiagnosisEventAuditInsert(String eventId, String eventType, String action, String result, String errorCode,
                                     String operatorRef, String reason, String correlationId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.action = action;
        this.result = result;
        this.errorCode = errorCode;
        this.operatorRef = operatorRef;
        this.reason = reason;
        this.correlationId = correlationId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getAction() { return action; }
    public String getResult() { return result; }
    public String getErrorCode() { return errorCode; }
    public String getOperatorRef() { return operatorRef; }
    public String getReason() { return reason; }
    public String getCorrelationId() { return correlationId; }
}
