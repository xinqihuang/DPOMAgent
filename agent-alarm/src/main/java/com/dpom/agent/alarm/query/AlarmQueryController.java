package com.dpom.agent.alarm.query;

import com.dpom.agent.alarm.persistence.AlarmQuery;
import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 告警查询 REST API：按来源/资源/服务/严重度/状态/时间区间过滤 + keyset 游标分页。
 */
@RestController
public class AlarmQueryController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final AlarmQueryService queryService;

    /**
     * 构造查询控制器。
     *
     * @param queryService 查询服务
     */
    public AlarmQueryController(AlarmQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 分页查询告警。
     *
     * @param source      来源过滤
     * @param resourceId  资源过滤
     * @param serviceCode 服务过滤
     * @param severity    严重度过滤
     * @param status      状态过滤
     * @param fromTime    起始时间
     * @param toTime      结束时间
     * @param cursorId    游标 id
     * @param limit       每页大小
     * @return 分页结果
     */
    @GetMapping("/api/v1/alarms")
    public AlarmPage query(
            @RequestParam(required = false) AlarmSource source,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) SeverityLevel severity,
            @RequestParam(required = false) AlarmStatus status,
            @RequestParam(required = false) LocalDateTime fromTime,
            @RequestParam(required = false) LocalDateTime toTime,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
        AlarmQuery query = new AlarmQuery(source, resourceId, serviceCode, severity, status, fromTime, toTime,
                cursorId, bounded);
        return queryService.query(query);
    }
}
