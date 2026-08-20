package com.dpom.agent.alarm.query;

import com.dpom.agent.alarm.domain.AlarmAudit;
import com.dpom.agent.alarm.domain.AlarmIncident;
import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.AlarmIncidentDao;
import com.dpom.agent.alarm.persistence.AlarmIncidentQuery;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 告警事件查询服务：事件分页 + 成员告警 + 审计时间线。
 */
@Service
public class AlarmIncidentQueryService {

    private final AlarmIncidentDao incidentDao;
    private final AlarmAuditDao auditDao;

    /**
     * 构造事件查询服务。
     *
     * @param incidentDao 事件持久化
     * @param auditDao    审计持久化
     */
    public AlarmIncidentQueryService(AlarmIncidentDao incidentDao, AlarmAuditDao auditDao) {
        this.incidentDao = incidentDao;
        this.auditDao = auditDao;
    }

    /**
     * 分页查询事件。
     *
     * @param query 查询参数
     * @return 分页结果
     */
    public AlarmIncidentPage query(AlarmIncidentQuery query) {
        List<AlarmIncident> items = incidentDao.search(query);
        Long nextCursor = items.size() == query.limit() ? items.get(items.size() - 1).id() : null;
        return new AlarmIncidentPage(items, nextCursor);
    }

    /**
     * 查询事件成员告警 id 列表。
     *
     * @param incidentId 事件 id
     * @return 成员告警 id 列表
     */
    public List<Long> findMembers(long incidentId) {
        return incidentDao.findMemberAlarmIds(incidentId);
    }

    /**
     * 查询事件审计时间线。
     *
     * @param incidentId 事件 id
     * @return 审计条目列表（按时间升序）
     */
    public List<AlarmAudit> auditTimeline(long incidentId) {
        return auditDao.findByTarget("INCIDENT", incidentId);
    }
}
