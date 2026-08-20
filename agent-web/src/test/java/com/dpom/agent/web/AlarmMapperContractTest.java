package com.dpom.agent.web;

import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.domain.AlarmAudit;
import com.dpom.agent.alarm.domain.AlarmIncident;
import com.dpom.agent.alarm.domain.AlarmSuppression;
import com.dpom.agent.alarm.domain.NotificationChannel;
import com.dpom.agent.alarm.domain.NotificationRecord;
import com.dpom.agent.alarm.domain.NotificationRule;
import com.dpom.agent.alarm.domain.NotificationStatus;
import com.dpom.agent.alarm.domain.SuppressionKind;
import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.AlarmDao;
import com.dpom.agent.alarm.persistence.AlarmIncidentDao;
import com.dpom.agent.alarm.persistence.AlarmSuppressionDao;
import com.dpom.agent.alarm.persistence.NotificationRecordDao;
import com.dpom.agent.alarm.persistence.NotificationRuleDao;
import com.dpom.agent.alarm.persistence.command.AlarmAuditInsert;
import com.dpom.agent.alarm.persistence.command.AlarmIncidentInsert;
import com.dpom.agent.alarm.persistence.command.AlarmInsert;
import com.dpom.agent.alarm.persistence.command.AlarmSuppressionInsert;
import com.dpom.agent.alarm.persistence.command.NotificationRecordInsert;
import com.dpom.agent.alarm.persistence.command.NotificationRuleInsert;
import com.dpom.agent.common.alarm.AlarmIncidentStatus;
import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 告警中台 Mapper 契约测试（H2，MySQL 模式）：验证枚举往返、可空字段与成员关联。
 */
@SpringBootTest
class AlarmMapperContractTest {

    @Autowired
    private AlarmDao alarmDao;

    @Autowired
    private AlarmIncidentDao alarmIncidentDao;

    @Autowired
    private NotificationRuleDao notificationRuleDao;

    @Autowired
    private NotificationRecordDao notificationRecordDao;

    @Autowired
    private AlarmSuppressionDao alarmSuppressionDao;

    @Autowired
    private AlarmAuditDao alarmAuditDao;

    @Test
    void alarmInsertAndSelectRoundTrip() {
        LocalDateTime now = LocalDateTime.now();
        AlarmInsert command = new AlarmInsert(AlarmSource.APM, "webhook", "ext-1", "fp-1", "res-1",
                "高错误率", SeverityLevel.CRITICAL, AlarmStatus.FIRING, 1, now, now,
                "asset-service", "prod", "{\"traceId\":\"t1\"}", null);
        alarmDao.insert(command);
        long id = command.getId();
        assertThat(id).isPositive();

        Alarm alarm = alarmDao.findById(id).orElseThrow();
        assertThat(alarm.source()).isEqualTo(AlarmSource.APM);
        assertThat(alarm.severity()).isEqualTo(SeverityLevel.CRITICAL);
        assertThat(alarm.status()).isEqualTo(AlarmStatus.FIRING);
        assertThat(alarm.occurrenceCount()).isEqualTo(1);
        assertThat(alarm.rawPayload()).isEqualTo("{\"traceId\":\"t1\"}");
        assertThat(alarm.samplePayloads()).isNull();
        assertThat(alarm.ingestedAt()).isNotNull();
    }

    @Test
    void alarmFindByFingerprintReturnsLatest() {
        LocalDateTime base = LocalDateTime.now();
        AlarmInsert first = new AlarmInsert(AlarmSource.CES, "poll", null, "fp-x", "res-x",
                "CPU 高", SeverityLevel.WARNING, AlarmStatus.FIRING, 1, base.minusMinutes(10),
                base.minusMinutes(10), "svc", "dev", "{}", null);
        alarmDao.insert(first);
        AlarmInsert second = new AlarmInsert(AlarmSource.CES, "poll", null, "fp-x", "res-x",
                "CPU 高", SeverityLevel.WARNING, AlarmStatus.FIRING, 1, base, base, "svc", "dev", "{}", null);
        alarmDao.insert(second);

        Alarm latest = alarmDao.findLatestByFingerprint("fp-x").orElseThrow();
        assertThat(latest.id()).isEqualTo(second.getId());
    }

    @Test
    void alarmIncidentInsertMemberAndSelectRoundTrip() {
        LocalDateTime now = LocalDateTime.now();
        AlarmIncidentInsert command = new AlarmIncidentInsert(AlarmIncidentStatus.OPEN, SeverityLevel.CRITICAL,
                "asset-service", "prod", "TIME_WINDOW_TOPOLOGY", "数据库无记录", now, null);
        alarmIncidentDao.insert(command);
        long incidentId = command.getId();
        assertThat(incidentId).isPositive();

        alarmIncidentDao.addMember(incidentId, 101L);
        alarmIncidentDao.addMember(incidentId, 102L);
        List<Long> members = alarmIncidentDao.findMemberAlarmIds(incidentId);
        assertThat(members).containsExactly(101L, 102L);

        AlarmIncident incident = alarmIncidentDao.findById(incidentId).orElseThrow();
        assertThat(incident.status()).isEqualTo(AlarmIncidentStatus.OPEN);
        assertThat(incident.severity()).isEqualTo(SeverityLevel.CRITICAL);
        assertThat(incident.escalationCandidate()).isFalse();
        assertThat(incident.endedAt()).isNull();
        assertThat(incident.createdAt()).isNotNull();
    }

    @Test
    void notificationRuleInsertFindEnabledAndToggle() {
        NotificationRuleInsert command = new NotificationRuleInsert("critical-notify", AlarmSource.APM,
                "asset-service", null, SeverityLevel.CRITICAL, null, "[\"EMAIL\",\"IM_WEBHOOK\"]", true);
        notificationRuleDao.insert(command);
        long ruleId = command.getId();
        assertThat(ruleId).isPositive();

        NotificationRule rule = notificationRuleDao.findById(ruleId).orElseThrow();
        assertThat(rule.sourceFilter()).isEqualTo(AlarmSource.APM);
        assertThat(rule.severityFilter()).isEqualTo(SeverityLevel.CRITICAL);
        assertThat(rule.enabled()).isTrue();

        assertThat(notificationRuleDao.findAllEnabled()).hasSizeGreaterThanOrEqualTo(1);
        int affected = notificationRuleDao.updateEnabled(ruleId, false);
        assertThat(affected).isEqualTo(1);
        assertThat(notificationRuleDao.findById(ruleId).orElseThrow().enabled()).isFalse();
    }

    @Test
    void notificationRecordInsertAndFindByIncident() {
        LocalDateTime now = LocalDateTime.now();
        NotificationRecordInsert command = new NotificationRecordInsert(1L, null, NotificationChannel.EMAIL,
                "sre@x.com", NotificationStatus.SENT, null, now);
        notificationRecordDao.insert(command);
        List<NotificationRecord> records = notificationRecordDao.findByIncidentId(1L);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(records.get(0).status()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void suppressionActiveWindowLookup() {
        LocalDateTime now = LocalDateTime.now();
        AlarmSuppressionInsert active = new AlarmSuppressionInsert(SuppressionKind.SILENCE, "res-1",
                "变更窗口", now.minusMinutes(10), now.plusMinutes(10), "sre");
        alarmSuppressionDao.insert(active);
        AlarmSuppressionInsert expired = new AlarmSuppressionInsert(SuppressionKind.SUPPRESSION, "res-1",
                "旧抑制", now.minusHours(2), now.minusHours(1), "sre");
        alarmSuppressionDao.insert(expired);

        List<AlarmSuppression> activeList = alarmSuppressionDao.findActiveByMatchKey("res-1", now);
        assertThat(activeList).hasSize(1);
        assertThat(activeList.get(0).kind()).isEqualTo(SuppressionKind.SILENCE);
    }

    @Test
    void auditInsertAndTimelineLookup() {
        alarmAuditDao.insert(new AlarmAuditInsert("NOTIFY", "INCIDENT", 1L, "sre", "邮件已发送", "SENT"));
        alarmAuditDao.insert(new AlarmAuditInsert("ACKNOWLEDGE", "INCIDENT", 1L, "sre", "认领", "OK"));
        List<AlarmAudit> timeline = alarmAuditDao.findByTarget("INCIDENT", 1L);
        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).action()).isEqualTo("NOTIFY");
        assertThat(timeline.get(1).action()).isEqualTo("ACKNOWLEDGE");
    }
}
