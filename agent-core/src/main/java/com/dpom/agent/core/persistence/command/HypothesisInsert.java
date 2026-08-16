package com.dpom.agent.core.persistence.command;

import com.dpom.agent.core.hypothesis.HypothesisStatus;

/**
 * HypothesisInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class HypothesisInsert {

    private Long id;
    private final long investigationId;
    private final Long parentId;
    private final String description;
    private final HypothesisStatus status;
    private final String missingChecks;

    /**
     * 构造插入命令。
     */
    public HypothesisInsert(long investigationId, Long parentId, String description, HypothesisStatus status, String missingChecks) {
        this.investigationId = investigationId;
        this.parentId = parentId;
        this.description = description;
        this.status = status;
        this.missingChecks = missingChecks;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getInvestigationId() { return investigationId; }
    public Long getParentId() { return parentId; }
    public String getDescription() { return description; }
    public HypothesisStatus getStatus() { return status; }
    public String getMissingChecks() { return missingChecks; }
}
