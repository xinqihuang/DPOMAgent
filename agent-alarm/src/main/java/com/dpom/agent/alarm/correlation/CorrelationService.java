package com.dpom.agent.alarm.correlation;

import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.domain.AlarmIncident;
import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.AlarmIncidentDao;
import com.dpom.agent.alarm.persistence.command.AlarmAuditInsert;
import com.dpom.agent.alarm.persistence.command.AlarmIncidentInsert;
import com.dpom.agent.common.alarm.AlarmIncidentTriggerPort;
import com.dpom.agent.common.alarm.AlarmIncidentTriggerRequest;
import com.dpom.agent.common.alarm.AlarmIncidentTriggerResult;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 关联编排服务：运行关联引擎 → 持久化事件与成员 → 满足触发条件时经端口触发诊断。
 *
 * <p>端口未装配时安全降级（记录跳过、不抛异常）。触发条件为聚合严重度 CRITICAL。</p>
 */
@Service
public class CorrelationService {

    private static final Logger LOG = LoggerFactory.getLogger(CorrelationService.class);
    private static final String TARGET_TYPE = "INCIDENT";

    private final AlarmCorrelationEngine engine;
    private final AlarmIncidentDao incidentDao;
    private final AlarmAuditDao auditDao;
    private final Optional<AlarmIncidentTriggerPort> triggerPort;

    /**
     * 构造关联编排服务。
     *
     * @param engine      关联引擎
     * @param incidentDao 事件持久化
     * @param auditDao    审计持久化
     * @param triggerPort 触发端口（可为空，表示诊断联动未启用）
     */
    public CorrelationService(AlarmCorrelationEngine engine, AlarmIncidentDao incidentDao,
            AlarmAuditDao auditDao, Optional<AlarmIncidentTriggerPort> triggerPort) {
        this.engine = engine;
        this.incidentDao = incidentDao;
        this.auditDao = auditDao;
        this.triggerPort = triggerPort;
    }

    /**
     * 关联并持久化告警，对满足触发条件的事件触发诊断。
     *
     * @param alarms 告警列表
     * @return 新建事件 id 列表
     */
    public List<Long> correlateAndPersist(List<Alarm> alarms) {
        List<CorrelatedIncident> candidates = engine.correlate(alarms);
        List<Long> incidentIds = new ArrayList<>();
        for (CorrelatedIncident ci : candidates) {
            long incidentId = persistIncident(ci);
            incidentIds.add(incidentId);
            maybeTrigger(incidentId, ci);
        }
        return incidentIds;
    }

    private long persistIncident(CorrelatedIncident ci) {
        AlarmIncident incident = ci.incident();
        AlarmIncidentInsert command = new AlarmIncidentInsert(incident.status(), incident.severity(),
                incident.serviceCode(), incident.environment(), incident.correlationBasis(),
                incident.summary(), incident.startedAt(), incident.endedAt());
        incidentDao.insert(command);
        long incidentId = command.getId();
        for (Long alarmId : ci.memberAlarmIds()) {
            incidentDao.addMember(incidentId, alarmId);
        }
        auditDao.insert(new AlarmAuditInsert("CORRELATE", TARGET_TYPE, incidentId, null,
                "basis=" + incident.correlationBasis() + ",members=" + ci.memberAlarmIds().size(), "OK"));
        return incidentId;
    }

    private void maybeTrigger(long incidentId, CorrelatedIncident ci) {
        AlarmIncident incident = ci.incident();
        if (incident.severity() != SeverityLevel.CRITICAL) {
            return;
        }
        if (triggerPort.isEmpty()) {
            auditDao.insert(new AlarmAuditInsert("TRIGGER_SKIP", TARGET_TYPE, incidentId, null,
                    "端口未装配，安全降级", "SKIPPED"));
            LOG.info("事件 {} 触发端口未装配，跳过诊断触发", incidentId);
            return;
        }
        AlarmIncidentTriggerRequest request = new AlarmIncidentTriggerRequest(incidentId,
                incident.serviceCode(), incident.environment(), incident.severity(), incident.summary(),
                ci.memberAlarmIds(), incident.startedAt());
        AlarmIncidentTriggerResult result = triggerPort.get().trigger(request);
        if (result.triggered()) {
            auditDao.insert(new AlarmAuditInsert("TRIGGER", TARGET_TYPE, incidentId, null,
                    "investigationId=" + result.investigationId(), "OK"));
            LOG.info("事件 {} 触发诊断 investigationId={}", incidentId, result.investigationId());
        } else {
            auditDao.insert(new AlarmAuditInsert("TRIGGER_SKIP", TARGET_TYPE, incidentId, null,
                    result.skipReason(), "SKIPPED"));
            LOG.info("事件 {} 触发诊断被跳过：{}", incidentId, result.skipReason());
        }
    }
}
