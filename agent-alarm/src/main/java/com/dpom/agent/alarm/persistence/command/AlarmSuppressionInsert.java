package com.dpom.agent.alarm.persistence.command;

import com.dpom.agent.alarm.domain.SuppressionKind;

import java.time.LocalDateTime;

/**
 * AlarmSuppressionInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class AlarmSuppressionInsert {

    private Long id;
    private final SuppressionKind kind;
    private final String matchKey;
    private final String reason;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;
    private final String createdBy;

    /**
     * 构造插入命令。
     */
    public AlarmSuppressionInsert(SuppressionKind kind, String matchKey, String reason, LocalDateTime startAt,
            LocalDateTime endAt, String createdBy) {
        this.kind = kind;
        this.matchKey = matchKey;
        this.reason = reason;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SuppressionKind getKind() {
        return kind;
    }

    public String getMatchKey() {
        return matchKey;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
