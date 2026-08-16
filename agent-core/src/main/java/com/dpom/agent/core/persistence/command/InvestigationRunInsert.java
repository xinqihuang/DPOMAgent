package com.dpom.agent.core.persistence.command;


/**
 * InvestigationRunInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class InvestigationRunInsert {

    private Long id;
    private final long investigationId;
    private final String modelVersion;
    private final String promptVersion;
    private final String toolsetVersion;

    /**
     * 构造插入命令。
     */
    public InvestigationRunInsert(long investigationId, String modelVersion, String promptVersion, String toolsetVersion) {
        this.investigationId = investigationId;
        this.modelVersion = modelVersion;
        this.promptVersion = promptVersion;
        this.toolsetVersion = toolsetVersion;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getInvestigationId() { return investigationId; }
    public String getModelVersion() { return modelVersion; }
    public String getPromptVersion() { return promptVersion; }
    public String getToolsetVersion() { return toolsetVersion; }
}
