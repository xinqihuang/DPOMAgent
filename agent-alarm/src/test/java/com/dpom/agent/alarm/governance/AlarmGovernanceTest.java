package com.dpom.agent.alarm.governance;

import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * 严重度分级器与告警分组器单测。
 */
@ExtendWith(MockitoExtension.class)
class AlarmGovernanceTest {

    @Mock
    private AlarmAuditDao auditDao;

    @Test
    void graderDefaultRulesAndVersion() {
        SeverityGrader grader = new SeverityGrader(auditDao);
        assertThat(grader.version()).isEqualTo("default-v1");
        assertThat(grader.grade(AlarmSource.AOM, "Major")).isEqualTo(SeverityLevel.CRITICAL);
        assertThat(grader.grade(AlarmSource.APM, "Fatal")).isEqualTo(SeverityLevel.CRITICAL);
        assertThat(grader.grade(AlarmSource.AOM, "unknown")).isEqualTo(SeverityLevel.WARNING);
        assertThat(grader.grade(AlarmSource.CES, null)).isEqualTo(SeverityLevel.WARNING);
    }

    @Test
    void graderReplaceRulesUpdatesAndAudits() {
        SeverityGrader grader = new SeverityGrader(auditDao);
        grader.replaceRules(List.of(new SeverityGradingRule(AlarmSource.AOM, "Minor", SeverityLevel.CRITICAL)),
                "custom-v2", "sre");
        assertThat(grader.version()).isEqualTo("custom-v2");
        assertThat(grader.grade(AlarmSource.AOM, "Minor")).isEqualTo(SeverityLevel.CRITICAL);
        assertThat(grader.grade(AlarmSource.AOM, "Major")).isEqualTo(SeverityLevel.WARNING);
        verify(auditDao).insert(any());
    }

    @Test
    void grouperGroupsByServiceAndResource() {
        AlarmGrouper grouper = new AlarmGrouper();
        LocalDateTime now = LocalDateTime.now();
        Alarm a = alarm(1L, "svc", "res-1", now);
        Alarm b = alarm(2L, "svc", "res-1", now);
        Alarm c = alarm(3L, "svc", "res-2", now);
        Map<String, List<Alarm>> groups = grouper.groupByServiceAndResource(List.of(a, b, c));
        assertThat(groups).hasSize(2);
        assertThat(groups.get("svc|res-1")).hasSize(2);
        assertThat(groups.get("svc|res-2")).hasSize(1);
    }

    private static Alarm alarm(long id, String service, String resource, LocalDateTime occurred) {
        return new Alarm(id, AlarmSource.AOM, "webhook", null, "fp", resource, "name",
                SeverityLevel.CRITICAL, AlarmStatus.FIRING, 1, occurred, occurred, occurred,
                service, "prod", "{}", null);
    }
}
