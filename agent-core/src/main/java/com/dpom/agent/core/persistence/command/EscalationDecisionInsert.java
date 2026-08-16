package com.dpom.agent.core.persistence.command;


/**
 * EscalationDecisionInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class EscalationDecisionInsert {

    private Long id;
    private final long investigationId;
    private final boolean eligible;
    private final String reasons;
    private final String missingEvidence;
    private final int confidence;

    /**
     * 构造插入命令。
     */
    public EscalationDecisionInsert(long investigationId, boolean eligible, String reasons, String missingEvidence, int confidence) {
        this.investigationId = investigationId;
        this.eligible = eligible;
        this.reasons = reasons;
        this.missingEvidence = missingEvidence;
        this.confidence = confidence;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getInvestigationId() { return investigationId; }
    public boolean getEligible() { return eligible; }
    public String getReasons() { return reasons; }
    public String getMissingEvidence() { return missingEvidence; }
    public int getConfidence() { return confidence; }
}
