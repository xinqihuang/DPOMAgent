package com.dpom.agent.web;

import com.dpom.agent.alarm.correlation.CorrelationService;
import com.dpom.agent.alarm.correlation.TopologySource;
import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.domain.AlarmAudit;
import com.dpom.agent.alarm.ingestion.AlarmIngestionService;
import com.dpom.agent.alarm.notification.HandlingArtifactService;
import com.dpom.agent.alarm.notification.NotificationMatchInput;
import com.dpom.agent.alarm.notification.NotificationOrchestrator;
import com.dpom.agent.alarm.notification.NotificationRuleAdminService;
import com.dpom.agent.alarm.persistence.AlarmDao;
import com.dpom.agent.alarm.persistence.AlarmIncidentQuery;
import com.dpom.agent.alarm.persistence.AlarmQuery;
import com.dpom.agent.alarm.persistence.NotificationRecordDao;
import com.dpom.agent.alarm.persistence.command.NotificationRuleInsert;
import com.dpom.agent.alarm.query.AlarmIncidentPage;
import com.dpom.agent.alarm.query.AlarmIncidentQueryService;
import com.dpom.agent.alarm.query.AlarmPage;
import com.dpom.agent.alarm.query.AlarmQueryService;
import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 告警中台端到端验收：接入 → 去重 → 关联事件化 → 通知 → 处置工件（REQUIRES_APPROVAL）→ 审计时间线。
 *
 * <p>使用真实 agent-core 端口实现（创建 Investigation 与 ScriptArtifact），H2 内存库，不触达外部 HTTP。</p>
 */
@SpringBootTest
class AlarmMiddlePlatformE2ETest {

    @Autowired
    private AlarmIngestionService ingestionService;
    @Autowired
    private AlarmDao alarmDao;
    @Autowired
    private CorrelationService correlationService;
    @Autowired
    private AlarmIncidentQueryService incidentQueryService;
    @Autowired
    private AlarmQueryService alarmQueryService;
    @Autowired
    private NotificationRuleAdminService ruleAdminService;
    @Autowired
    private NotificationOrchestrator notificationOrchestrator;
    @Autowired
    private HandlingArtifactService handlingArtifactService;
    @Autowired
    private NotificationRecordDao recordDao;

    @TestConfiguration
    static class TestTopologyConfig {
        @Bean
        @Primary
        TopologySource testTopologySource() {
            return new TestTopology();
        }
    }

    static final class TestTopology implements TopologySource {
        @Override
        public Set<String> adjacentResources(String resourceId) {
            return "res-1".equals(resourceId) ? Set.of("res-2")
                    : "res-2".equals(resourceId) ? Set.of("res-1") : Set.of();
        }

        @Override
        public boolean isAdjacent(String a, String b) {
            if (a == null || b == null) {
                return false;
            }
            return a.equals(b) || Set.of(a, b).equals(Set.of("res-1", "res-2"));
        }
    }

    @Test
    void fullChainFromIngestionToArtifactToAudit() {
        ruleAdminService.addRule(new NotificationRuleInsert("e2e-critical-email", null, null, null,
                SeverityLevel.CRITICAL, null, "[{\"channel\":\"EMAIL\",\"recipient\":\"sre@x.com\"}]", true),
                "e2e");

        long alarmId1 = ingest("ext-1", "res-1", "Critical", "2026-08-19T10:00:00");
        long alarmId2 = ingest("ext-2", "res-2", "Critical", "2026-08-19T10:01:00");

        AlarmPage page = alarmQueryService.query(
                new AlarmQuery(AlarmSource.AOM, null, "e2e-svc", null, null, null, null, null, 10));
        assertThat(page.items()).hasSize(2);

        Alarm a1 = alarmDao.findById(alarmId1).orElseThrow();
        Alarm a2 = alarmDao.findById(alarmId2).orElseThrow();
        List<Long> incidentIds = correlationService.correlateAndPersist(List.of(a1, a2));
        assertThat(incidentIds).hasSize(1);
        long incidentId = incidentIds.get(0);

        AlarmIncidentPage incidents = incidentQueryService.query(
                new AlarmIncidentQuery(null, null, "e2e-svc", null, null, null, 10));
        assertThat(incidents.items()).hasSize(1);
        assertThat(incidentQueryService.findMembers(incidentId)).containsExactlyInAnyOrder(alarmId1, alarmId2);

        notificationOrchestrator.notify(incidentId, new NotificationMatchInput(AlarmSource.AOM, "e2e-svc",
                "res-1", SeverityLevel.CRITICAL, java.util.Map.of()), "事件主题", "事件正文");
        assertThat(recordDao.findByIncidentId(incidentId)).isNotEmpty();

        long investigationId = extractInvestigationId(incidentQueryService.auditTimeline(incidentId));
        handlingArtifactService.generateArtifact(incidentId, investigationId, "shell", "重启服务", "HIGH",
                "#!/bin/bash\n");

        List<AlarmAudit> timeline = incidentQueryService.auditTimeline(incidentId);
        assertThat(timeline).extracting(AlarmAudit::action)
                .contains("CORRELATE", "TRIGGER", "ARTIFACT_GENERATE");
        assertThat(timeline).anySatisfy(aud -> assertThat(aud.detail()).contains("REQUIRES_APPROVAL"));
    }

    private static long extractInvestigationId(List<AlarmAudit> timeline) {
        return timeline.stream().filter(a -> "TRIGGER".equals(a.action())).findFirst()
                .map(a -> Long.parseLong(a.detail().replace("investigationId=", ""))).orElseThrow();
    }

    private long ingest(String externalId, String resource, String severity, String occurredAt) {
        String payload = "{\"id\":\"" + externalId + "\",\"resource\":\"" + resource + "\","
                + "\"name\":\"HighCpu\",\"severity\":\"" + severity + "\",\"status\":\"FIRING\","
                + "\"occurredAt\":\"" + occurredAt + "\",\"service\":\"e2e-svc\",\"environment\":\"prod\"}";
        return ingestionService.ingest(AlarmSource.AOM, payload, "webhook").alarmId();
    }
}
