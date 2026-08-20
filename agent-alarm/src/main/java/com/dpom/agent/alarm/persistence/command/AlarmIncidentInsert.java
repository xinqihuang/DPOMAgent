package com.dpom.agent.alarm.persistence.command;

import com.dpom.agent.common.alarm.AlarmIncidentStatus;
import com.dpom.agent.common.alarm.SeverityLevel;

import java.time.LocalDateTime;

/**
 * AlarmIncidentInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class AlarmIncidentInsert {

    private Long id;
    private final AlarmIncidentStatus status;
    private final SeverityLevel severity;
    private final String serviceCode;
    private final String environment;
    private final String correlationBasis;
    private final String summary;
    private final LocalDateTime startedAt;
    private final LocalDateTime endedAt;

    /**
     * 构造插入命令。
     */
    public AlarmIncidentInsert(AlarmIncidentStatus status, SeverityLevel severity, String serviceCode,
            String environment, String correlationBasis, String summary, LocalDateTime startedAt,
            LocalDateTime endedAt) {
        this.status = status;
        this.severity = severity;
        this.serviceCode = serviceCode;
        this.environment = environment;
        this.correlationBasis = correlationBasis;
        this.summary = summary;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AlarmIncidentStatus getStatus() {
        return status;
    }

    public SeverityLevel getSeverity() {
        return severity;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getCorrelationBasis() {
        return correlationBasis;
    }

    public String getSummary() {
        return summary;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }
}
