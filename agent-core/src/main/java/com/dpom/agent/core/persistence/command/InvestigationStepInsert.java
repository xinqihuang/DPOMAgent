package com.dpom.agent.core.persistence.command;


/**
 * InvestigationStepInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class InvestigationStepInsert {

    private Long id;
    private final long investigationId;
    private final Long runId;
    private final int stepOrder;
    private final String stepType;
    private final String summary;
    private final String payloadJson;

    /**
     * 构造插入命令。
     */
    public InvestigationStepInsert(long investigationId, Long runId, int stepOrder, String stepType, String summary, String payloadJson) {
        this.investigationId = investigationId;
        this.runId = runId;
        this.stepOrder = stepOrder;
        this.stepType = stepType;
        this.summary = summary;
        this.payloadJson = payloadJson;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getInvestigationId() { return investigationId; }
    public Long getRunId() { return runId; }
    public int getStepOrder() { return stepOrder; }
    public String getStepType() { return stepType; }
    public String getSummary() { return summary; }
    public String getPayloadJson() { return payloadJson; }
}
