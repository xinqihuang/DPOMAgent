package com.dpom.agent.alarm.persistence.command;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;

import java.time.LocalDateTime;

/**
 * AlarmInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class AlarmInsert {

    private Long id;
    private final AlarmSource source;
    private final String ingestionMode;
    private final String externalId;
    private final String fingerprint;
    private final String resourceId;
    private final String alarmName;
    private final SeverityLevel severity;
    private final AlarmStatus status;
    private final int occurrenceCount;
    private final LocalDateTime firstOccurredAt;
    private final LocalDateTime lastOccurredAt;
    private final String serviceCode;
    private final String environment;
    private final String rawPayload;
    private final String samplePayloads;

    /**
     * 构造插入命令。
     */
    public AlarmInsert(AlarmSource source, String ingestionMode, String externalId, String fingerprint,
            String resourceId, String alarmName, SeverityLevel severity, AlarmStatus status, int occurrenceCount,
            LocalDateTime firstOccurredAt, LocalDateTime lastOccurredAt, String serviceCode, String environment,
            String rawPayload, String samplePayloads) {
        this.source = source;
        this.ingestionMode = ingestionMode;
        this.externalId = externalId;
        this.fingerprint = fingerprint;
        this.resourceId = resourceId;
        this.alarmName = alarmName;
        this.severity = severity;
        this.status = status;
        this.occurrenceCount = occurrenceCount;
        this.firstOccurredAt = firstOccurredAt;
        this.lastOccurredAt = lastOccurredAt;
        this.serviceCode = serviceCode;
        this.environment = environment;
        this.rawPayload = rawPayload;
        this.samplePayloads = samplePayloads;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AlarmSource getSource() {
        return source;
    }

    public String getIngestionMode() {
        return ingestionMode;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getAlarmName() {
        return alarmName;
    }

    public SeverityLevel getSeverity() {
        return severity;
    }

    public AlarmStatus getStatus() {
        return status;
    }

    public int getOccurrenceCount() {
        return occurrenceCount;
    }

    public LocalDateTime getFirstOccurredAt() {
        return firstOccurredAt;
    }

    public LocalDateTime getLastOccurredAt() {
        return lastOccurredAt;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public String getSamplePayloads() {
        return samplePayloads;
    }
}
