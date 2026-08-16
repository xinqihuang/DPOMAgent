package com.dpom.agent.core.persistence.command;


/**
 * ObservationInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class ObservationInsert {

    private Long id;
    private final long investigationId;
    private final Long runId;
    private final String source;
    private final String artifactRef;
    private final String location;
    private final String supportsHypothesisIds;
    private final String contradictsHypothesisIds;
    private final String summary;
    private final String payloadJson;

    /**
     * 构造插入命令。
     */
    public ObservationInsert(long investigationId, Long runId, String source, String artifactRef, String location, String supportsHypothesisIds, String contradictsHypothesisIds, String summary, String payloadJson) {
        this.investigationId = investigationId;
        this.runId = runId;
        this.source = source;
        this.artifactRef = artifactRef;
        this.location = location;
        this.supportsHypothesisIds = supportsHypothesisIds;
        this.contradictsHypothesisIds = contradictsHypothesisIds;
        this.summary = summary;
        this.payloadJson = payloadJson;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getInvestigationId() { return investigationId; }
    public Long getRunId() { return runId; }
    public String getSource() { return source; }
    public String getArtifactRef() { return artifactRef; }
    public String getLocation() { return location; }
    public String getSupportsHypothesisIds() { return supportsHypothesisIds; }
    public String getContradictsHypothesisIds() { return contradictsHypothesisIds; }
    public String getSummary() { return summary; }
    public String getPayloadJson() { return payloadJson; }
}
