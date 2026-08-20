package com.dpom.agent.alarm.persistence.command;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;

/**
 * NotificationRuleInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class NotificationRuleInsert {

    private Long id;
    private final String name;
    private final AlarmSource sourceFilter;
    private final String serviceCodeFilter;
    private final String resourceFilter;
    private final SeverityLevel severityFilter;
    private final String tagFilter;
    private final String channels;
    private final boolean enabled;

    /**
     * 构造插入命令。
     */
    public NotificationRuleInsert(String name, AlarmSource sourceFilter, String serviceCodeFilter,
            String resourceFilter, SeverityLevel severityFilter, String tagFilter, String channels,
            boolean enabled) {
        this.name = name;
        this.sourceFilter = sourceFilter;
        this.serviceCodeFilter = serviceCodeFilter;
        this.resourceFilter = resourceFilter;
        this.severityFilter = severityFilter;
        this.tagFilter = tagFilter;
        this.channels = channels;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public AlarmSource getSourceFilter() {
        return sourceFilter;
    }

    public String getServiceCodeFilter() {
        return serviceCodeFilter;
    }

    public String getResourceFilter() {
        return resourceFilter;
    }

    public SeverityLevel getSeverityFilter() {
        return severityFilter;
    }

    public String getTagFilter() {
        return tagFilter;
    }

    public String getChannels() {
        return channels;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
