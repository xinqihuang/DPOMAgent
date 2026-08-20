package com.dpom.agent.alarm.persistence.command;

import com.dpom.agent.alarm.domain.NotificationChannel;
import com.dpom.agent.alarm.domain.NotificationStatus;

import java.time.LocalDateTime;

/**
 * NotificationRecordInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class NotificationRecordInsert {

    private Long id;
    private final long incidentId;
    private final Long ruleId;
    private final NotificationChannel channel;
    private final String recipient;
    private final NotificationStatus status;
    private final String errorMessage;
    private final LocalDateTime sentAt;

    /**
     * 构造插入命令。
     */
    public NotificationRecordInsert(long incidentId, Long ruleId, NotificationChannel channel, String recipient,
            NotificationStatus status, String errorMessage, LocalDateTime sentAt) {
        this.incidentId = incidentId;
        this.ruleId = ruleId;
        this.channel = channel;
        this.recipient = recipient;
        this.status = status;
        this.errorMessage = errorMessage;
        this.sentAt = sentAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getIncidentId() {
        return incidentId;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
