package com.dpom.agent.core.persistence.command;


/**
 * ScriptArtifactInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class ScriptArtifactInsert {

    private Long id;
    private final long investigationId;
    private final String type;
    private final String language;
    private final String purpose;
    private final String riskLevel;
    private final boolean readOnly;
    private final String approvalStatus;
    private final String preconditions;
    private final String verification;
    private final String rollback;
    private final String content;
    private final String hypothesesToValidate;
    private final String expectedOutput;
    private final String instructions;
    private final String rootCause;
    private final String evidenceIds;
    private final String target;

    /**
     * 构造插入命令。
     */
    public ScriptArtifactInsert(long investigationId, String type, String language, String purpose, String riskLevel, boolean readOnly, String approvalStatus, String preconditions, String verification, String rollback, String content, String hypothesesToValidate, String expectedOutput, String instructions, String rootCause, String evidenceIds, String target) {
        this.investigationId = investigationId;
        this.type = type;
        this.language = language;
        this.purpose = purpose;
        this.riskLevel = riskLevel;
        this.readOnly = readOnly;
        this.approvalStatus = approvalStatus;
        this.preconditions = preconditions;
        this.verification = verification;
        this.rollback = rollback;
        this.content = content;
        this.hypothesesToValidate = hypothesesToValidate;
        this.expectedOutput = expectedOutput;
        this.instructions = instructions;
        this.rootCause = rootCause;
        this.evidenceIds = evidenceIds;
        this.target = target;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getInvestigationId() { return investigationId; }
    public String getType() { return type; }
    public String getLanguage() { return language; }
    public String getPurpose() { return purpose; }
    public String getRiskLevel() { return riskLevel; }
    public boolean getReadOnly() { return readOnly; }
    public String getApprovalStatus() { return approvalStatus; }
    public String getPreconditions() { return preconditions; }
    public String getVerification() { return verification; }
    public String getRollback() { return rollback; }
    public String getContent() { return content; }
    public String getHypothesesToValidate() { return hypothesesToValidate; }
    public String getExpectedOutput() { return expectedOutput; }
    public String getInstructions() { return instructions; }
    public String getRootCause() { return rootCause; }
    public String getEvidenceIds() { return evidenceIds; }
    public String getTarget() { return target; }
}
