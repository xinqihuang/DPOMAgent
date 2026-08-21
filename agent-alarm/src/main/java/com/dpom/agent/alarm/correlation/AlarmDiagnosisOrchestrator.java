package com.dpom.agent.alarm.correlation;

import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.domain.AlarmAudit;
import com.dpom.agent.alarm.notification.HandlingArtifactService;
import com.dpom.agent.alarm.notification.NotificationMatchInput;
import com.dpom.agent.alarm.notification.NotificationOrchestrator;
import com.dpom.agent.alarm.persistence.AlarmDao;
import com.dpom.agent.alarm.query.AlarmIncidentQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 告警诊断编排：关联事件化 → 通知分发 → 处置工件生成（REQUIRES_APPROVAL）。
 *
 * <p>供管理面「采集并诊断」调用，串联 {@link CorrelationService}、{@link NotificationOrchestrator}
 * 与 {@link HandlingArtifactService}。处置工件不执行生产操作、不持凭据。</p>
 */
@Service
public class AlarmDiagnosisOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(AlarmDiagnosisOrchestrator.class);
    private static final String ARTIFACT_SCRIPT = "#!/bin/bash\n# 由告警中台生成的处置脚本，需人工审批后执行\n";

    private final CorrelationService correlationService;
    private final AlarmDao alarmDao;
    private final NotificationOrchestrator notificationOrchestrator;
    private final AlarmIncidentQueryService incidentQueryService;
    private final HandlingArtifactService handlingArtifactService;

    /**
     * 构造诊断编排。
     *
     * @param correlationService    关联编排
     * @param alarmDao              告警持久化
     * @param notificationOrchestrator 通知编排
     * @param incidentQueryService  事件查询
     * @param handlingArtifactService 处置工件服务
     */
    public AlarmDiagnosisOrchestrator(CorrelationService correlationService, AlarmDao alarmDao,
            NotificationOrchestrator notificationOrchestrator, AlarmIncidentQueryService incidentQueryService,
            HandlingArtifactService handlingArtifactService) {
        this.correlationService = correlationService;
        this.alarmDao = alarmDao;
        this.notificationOrchestrator = notificationOrchestrator;
        this.incidentQueryService = incidentQueryService;
        this.handlingArtifactService = handlingArtifactService;
    }

    /**
     * 对指定告警执行关联 → 通知 → 处置工件全链路，返回新建事件 id 列表。
     *
     * @param alarmIds 告警 id 列表
     * @return 新建事件 id 列表
     */
    public List<Long> diagnose(List<Long> alarmIds) {
        List<Alarm> alarms = loadAlarms(alarmIds);
        if (alarms.isEmpty()) {
            return List.of();
        }
        List<Long> incidentIds = correlationService.correlateAndPersist(alarms);
        for (Long incidentId : incidentIds) {
            notifyAndArtifact(incidentId);
        }
        return incidentIds;
    }

    private List<Alarm> loadAlarms(List<Long> alarmIds) {
        List<Alarm> alarms = new ArrayList<>();
        for (Long id : alarmIds) {
            Optional<Alarm> alarm = alarmDao.findById(id);
            if (alarm.isPresent()) {
                alarms.add(alarm.get());
            } else {
                LOG.warn("诊断请求中的告警 id={} 不存在，已跳过", id);
            }
        }
        return alarms;
    }

    private void notifyAndArtifact(long incidentId) {
        List<Long> memberIds = incidentQueryService.findMembers(incidentId);
        if (memberIds.isEmpty()) {
            return;
        }
        Optional<Alarm> first = alarmDao.findById(memberIds.get(0));
        first.ifPresent(a -> dispatchNotification(incidentId, a));
        generateArtifactIfTriggered(incidentId);
    }

    private void dispatchNotification(long incidentId, Alarm alarm) {
        NotificationMatchInput input = new NotificationMatchInput(alarm.source(), alarm.serviceCode(),
                alarm.resourceId(), alarm.severity(), Map.of());
        String subject = "告警事件 #" + incidentId + "：" + alarm.alarmName();
        String body = "资源 " + alarm.resourceId() + " 触发 " + alarm.alarmName() + "，请排查。";
        notificationOrchestrator.notify(incidentId, input, subject, body);
    }

    private void generateArtifactIfTriggered(long incidentId) {
        List<AlarmAudit> timeline = incidentQueryService.auditTimeline(incidentId);
        Optional<Long> investigationId = extractInvestigationId(timeline);
        if (investigationId.isEmpty()) {
            return;
        }
        handlingArtifactService.generateArtifact(incidentId, investigationId.get(), "shell", "告警处置建议",
                "HIGH", ARTIFACT_SCRIPT);
    }

    private static Optional<Long> extractInvestigationId(List<AlarmAudit> timeline) {
        for (AlarmAudit audit : timeline) {
            if ("TRIGGER".equals(audit.action()) && audit.detail() != null
                    && audit.detail().startsWith("investigationId=")) {
                return Optional.of(Long.parseLong(audit.detail().substring("investigationId=".length())));
            }
        }
        return Optional.empty();
    }
}
