package com.dpom.agent.alarm.correlation;

import com.dpom.agent.alarm.domain.AlarmIncident;
import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.AlarmIncidentDao;
import com.dpom.agent.alarm.persistence.command.AlarmAuditInsert;
import com.dpom.agent.common.alarm.AlarmIncidentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 告警事件生命周期服务：状态流转（Open→Acknowledged→Resolved）、超时升级候选评估、状态变更审计。
 *
 * <p>非法状态流转抛出 {@link IllegalStateException}；所有变更写 {@code alarm_audit}。</p>
 */
@Service
public class AlarmIncidentLifecycleService {

    private static final Logger LOG = LoggerFactory.getLogger(AlarmIncidentLifecycleService.class);

    private static final String TARGET_TYPE = "INCIDENT";

    private final AlarmIncidentDao incidentDao;
    private final AlarmAuditDao auditDao;
    private final Duration ackTimeout;

    /**
     * 构造生命周期服务。
     *
     * @param incidentDao       事件持久化
     * @param auditDao          审计持久化
     * @param ackTimeoutMinutes 认领超时分钟数（{@code dpom.alarm.incident.ack-timeout-minutes}，默认 30）
     */
    public AlarmIncidentLifecycleService(AlarmIncidentDao incidentDao, AlarmAuditDao auditDao,
            @Value("${dpom.alarm.incident.ack-timeout-minutes:30}") long ackTimeoutMinutes) {
        this.incidentDao = incidentDao;
        this.auditDao = auditDao;
        this.ackTimeout = Duration.ofMinutes(ackTimeoutMinutes);
    }

    /**
     * 认领事件：OPEN → ACKNOWLEDGED。
     *
     * @param incidentId 事件 id
     * @param assignee   处理人
     */
    public void acknowledge(long incidentId, String assignee) {
        AlarmIncident incident = load(incidentId);
        if (incident.status() != AlarmIncidentStatus.OPEN) {
            throw new IllegalStateException("事件 " + incidentId + " 当前状态 " + incident.status() + " 不可认领");
        }
        LocalDateTime now = LocalDateTime.now();
        incidentDao.updateLifecycle(incidentId, AlarmIncidentStatus.ACKNOWLEDGED, assignee, now,
                null, incident.endedAt(), now);
        audit(incidentId, "ACKNOWLEDGE", assignee, "状态 OPEN→ACKNOWLEDGED", "OK");
        LOG.info("事件 {} 已被 {} 认领", incidentId, assignee);
    }

    /**
     * 闭环事件：OPEN/ACKNOWLEDGED → RESOLVED。
     *
     * @param incidentId 事件 id
     */
    public void resolve(long incidentId) {
        AlarmIncident incident = load(incidentId);
        if (incident.status() == AlarmIncidentStatus.RESOLVED) {
            throw new IllegalStateException("事件 " + incidentId + " 已闭环");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endedAt = incident.endedAt() == null ? now : incident.endedAt();
        incidentDao.updateLifecycle(incidentId, AlarmIncidentStatus.RESOLVED, incident.assignee(),
                incident.acknowledgedAt(), now, endedAt, now);
        audit(incidentId, "RESOLVE", incident.assignee(), "状态 " + incident.status() + "→RESOLVED", "OK");
        LOG.info("事件 {} 已闭环", incidentId);
    }

    /**
     * 评估升级候选：OPEN 且超认领超时未认领 → 标记升级候选。
     *
     * @param incidentId 事件 id
     * @return 标记为升级候选返回 true，否则 false
     */
    public boolean evaluateEscalation(long incidentId) {
        AlarmIncident incident = load(incidentId);
        if (incident.status() != AlarmIncidentStatus.OPEN) {
            return false;
        }
        if (incident.startedAt() == null) {
            return false;
        }
        if (Duration.between(incident.startedAt(), LocalDateTime.now()).compareTo(ackTimeout) <= 0) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        incidentDao.updateEscalation(incidentId, true, now, now);
        audit(incidentId, "ESCALATION_CANDIDATE", null, "超时未认领，标记升级候选", "OK");
        LOG.info("事件 {} 标记为升级候选", incidentId);
        return true;
    }

    private AlarmIncident load(long incidentId) {
        Optional<AlarmIncident> opt = incidentDao.findById(incidentId);
        if (opt.isEmpty()) {
            throw new IllegalStateException("事件 " + incidentId + " 不存在");
        }
        return opt.get();
    }

    private void audit(long incidentId, String action, String operator, String detail, String result) {
        auditDao.insert(new AlarmAuditInsert(action, TARGET_TYPE, incidentId, operator, detail, result));
    }
}
