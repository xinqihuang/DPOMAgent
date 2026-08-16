package com.dpom.agent.core.persistence.command;


/**
 * ConclusionInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class ConclusionInsert {

    private Long id;
    private final long investigationId;
    private final String resultType;
    private final String rootCauseId;
    private final String rootCause;
    private final String evidenceIds;
    private final String unresolvedQuestions;
    private final String summary;

    /**
     * 构造插入命令。
     */
    public ConclusionInsert(long investigationId, String resultType, String rootCauseId, String rootCause, String evidenceIds, String unresolvedQuestions, String summary) {
        this.investigationId = investigationId;
        this.resultType = resultType;
        this.rootCauseId = rootCauseId;
        this.rootCause = rootCause;
        this.evidenceIds = evidenceIds;
        this.unresolvedQuestions = unresolvedQuestions;
        this.summary = summary;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getInvestigationId() { return investigationId; }
    public String getResultType() { return resultType; }
    public String getRootCauseId() { return rootCauseId; }
    public String getRootCause() { return rootCause; }
    public String getEvidenceIds() { return evidenceIds; }
    public String getUnresolvedQuestions() { return unresolvedQuestions; }
    public String getSummary() { return summary; }
}
