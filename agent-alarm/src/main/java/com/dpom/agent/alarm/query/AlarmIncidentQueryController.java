package com.dpom.agent.alarm.query;

import com.dpom.agent.alarm.persistence.AlarmIncidentQuery;
import com.dpom.agent.common.alarm.AlarmIncidentStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 告警事件查询 REST API：事件分页、成员告警、审计时间线。
 */
@RestController
public class AlarmIncidentQueryController {

    private static final int MAX_LIMIT = 200;

    private final AlarmIncidentQueryService queryService;

    /**
     * 构造事件查询控制器。
     *
     * @param queryService 查询服务
     */
    public AlarmIncidentQueryController(AlarmIncidentQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 分页查询事件。
     *
     * @param status      状态过滤
     * @param severity    严重度过滤
     * @param serviceCode 服务过滤
     * @param fromTime    起始时间
     * @param toTime      结束时间
     * @param cursorId    游标 id
     * @param limit       每页大小
     * @return 分页结果
     */
    @GetMapping("/api/v1/alarm-incidents")
    public AlarmIncidentPage query(
            @RequestParam(required = false) AlarmIncidentStatus status,
            @RequestParam(required = false) SeverityLevel severity,
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) LocalDateTime fromTime,
            @RequestParam(required = false) LocalDateTime toTime,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
        AlarmIncidentQuery query = new AlarmIncidentQuery(status, severity, serviceCode, fromTime, toTime,
                cursorId, bounded);
        return queryService.query(query);
    }

    /**
     * 查询事件成员告警 id 列表。
     *
     * @param incidentId 事件 id
     * @return 成员告警 id 列表
     */
    @GetMapping("/api/v1/alarm-incidents/{incidentId}/members")
    public java.util.List<Long> members(@PathVariable long incidentId) {
        return queryService.findMembers(incidentId);
    }

    /**
     * 查询事件审计时间线。
     *
     * @param incidentId 事件 id
     * @return 审计条目列表
     */
    @GetMapping("/api/v1/alarm-incidents/{incidentId}/audit")
    public java.util.List<com.dpom.agent.alarm.domain.AlarmAudit> audit(@PathVariable long incidentId) {
        return queryService.auditTimeline(incidentId);
    }
}
