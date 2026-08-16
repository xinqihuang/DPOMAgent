package com.dpom.agent.core.persistence.command;

import com.dpom.agent.core.investigation.InvestigationStatus;

/**
 * InvestigationInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class InvestigationInsert {

    private Long id;
    private final long incidentId;
    private final InvestigationStatus status;
    private final Long currentRunId;
    private final int maxSteps;
    private final int maxToolCalls;
    private final int maxDurationSeconds;
    private final int maxNoProgressRounds;

    /**
     * 构造插入命令。
     */
    public InvestigationInsert(long incidentId, InvestigationStatus status, Long currentRunId, int maxSteps, int maxToolCalls, int maxDurationSeconds, int maxNoProgressRounds) {
        this.incidentId = incidentId;
        this.status = status;
        this.currentRunId = currentRunId;
        this.maxSteps = maxSteps;
        this.maxToolCalls = maxToolCalls;
        this.maxDurationSeconds = maxDurationSeconds;
        this.maxNoProgressRounds = maxNoProgressRounds;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getIncidentId() { return incidentId; }
    public InvestigationStatus getStatus() { return status; }
    public Long getCurrentRunId() { return currentRunId; }
    public int getMaxSteps() { return maxSteps; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public int getMaxDurationSeconds() { return maxDurationSeconds; }
    public int getMaxNoProgressRounds() { return maxNoProgressRounds; }
}
