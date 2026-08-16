package com.dpom.agent.core.persistence.command;


/**
 * ToolCallAuditInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class ToolCallAuditInsert {

    private Long id;
    private final long investigationId;
    private final Long runId;
    private final String toolName;
    private final String toolInput;
    private final String toolOutputSummary;
    private final Long durationMs;
    private final Boolean success;
    private final String errorMessage;

    /**
     * 构造插入命令。
     */
    public ToolCallAuditInsert(long investigationId, Long runId, String toolName, String toolInput, String toolOutputSummary, Long durationMs, Boolean success, String errorMessage) {
        this.investigationId = investigationId;
        this.runId = runId;
        this.toolName = toolName;
        this.toolInput = toolInput;
        this.toolOutputSummary = toolOutputSummary;
        this.durationMs = durationMs;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getInvestigationId() { return investigationId; }
    public Long getRunId() { return runId; }
    public String getToolName() { return toolName; }
    public String getToolInput() { return toolInput; }
    public String getToolOutputSummary() { return toolOutputSummary; }
    public Long getDurationMs() { return durationMs; }
    public Boolean getSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
}
