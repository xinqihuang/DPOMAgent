package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.domain.NotificationRule;
import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.NotificationRuleDao;
import com.dpom.agent.alarm.persistence.command.NotificationRuleInsert;
import com.dpom.agent.common.alarm.AlarmSource;
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
import static org.mockito.Mockito.when;

/**
 * 通知规则匹配引擎与管理服务单测。
 */
@ExtendWith(MockitoExtension.class)
class NotificationRuleMatcherTest {

    @Mock
    private NotificationRuleDao ruleDao;
    @Mock
    private AlarmAuditDao auditDao;

    @Test
    void matchesAllFilters() {
        when(ruleDao.findAllEnabled()).thenReturn(List.of(
                rule(1L, AlarmSource.AOM, "svc", "res-1", SeverityLevel.CRITICAL, "team=sre")));
        NotificationRuleMatcher matcher = new NotificationRuleMatcher(ruleDao);

        List<NotificationRule> matched = matcher.match(new NotificationMatchInput(
                AlarmSource.AOM, "svc", "res-1", SeverityLevel.CRITICAL, Map.of("team", "sre")));

        assertThat(matched).hasSize(1);
    }

    @Test
    void nullFiltersMatchAnything() {
        when(ruleDao.findAllEnabled()).thenReturn(List.of(
                rule(1L, null, null, null, null, null)));
        NotificationRuleMatcher matcher = new NotificationRuleMatcher(ruleDao);

        List<NotificationRule> matched = matcher.match(new NotificationMatchInput(
                AlarmSource.CES, "any", "any", SeverityLevel.INFO, Map.of()));

        assertThat(matched).hasSize(1);
    }

    @Test
    void noMatchReturnsEmpty() {
        when(ruleDao.findAllEnabled()).thenReturn(List.of(
                rule(1L, AlarmSource.AOM, "svc", null, SeverityLevel.CRITICAL, null)));
        NotificationRuleMatcher matcher = new NotificationRuleMatcher(ruleDao);

        List<NotificationRule> matched = matcher.match(new NotificationMatchInput(
                AlarmSource.AOM, "svc", null, SeverityLevel.WARNING, Map.of()));

        assertThat(matched).isEmpty();
    }

    @Test
    void multipleRulesMatch() {
        when(ruleDao.findAllEnabled()).thenReturn(List.of(
                rule(1L, null, null, null, SeverityLevel.CRITICAL, null),
                rule(2L, AlarmSource.AOM, null, null, null, null)));
        NotificationRuleMatcher matcher = new NotificationRuleMatcher(ruleDao);

        List<NotificationRule> matched = matcher.match(new NotificationMatchInput(
                AlarmSource.AOM, "svc", "res", SeverityLevel.CRITICAL, Map.of()));

        assertThat(matched).hasSize(2);
    }

    @Test
    void tagFilterRequiresAllPairs() {
        when(ruleDao.findAllEnabled()).thenReturn(List.of(
                rule(1L, null, null, null, null, "team=sre,env=prod")));
        NotificationRuleMatcher matcher = new NotificationRuleMatcher(ruleDao);

        assertThat(matcher.match(new NotificationMatchInput(
                null, null, null, null, Map.of("team", "sre", "env", "prod")))).hasSize(1);
        assertThat(matcher.match(new NotificationMatchInput(
                null, null, null, null, Map.of("team", "sre", "env", "staging")))).isEmpty();
    }

    @Test
    void adminServiceAuditsEnableAndAdd() {
        org.mockito.Mockito.doAnswer(inv -> {
            inv.getArgument(0, NotificationRuleInsert.class).setId(1L);
            return 1;
        }).when(ruleDao).insert(any());
        NotificationRuleAdminService admin = new NotificationRuleAdminService(ruleDao, auditDao);
        NotificationRuleInsert cmd = new NotificationRuleInsert("r", null, null, null, null, null,
                "[\"EMAIL\"]", true);

        admin.addRule(cmd, "alice");

        verify(ruleDao).insert(any());
        verify(auditDao).insert(any());
        admin.setEnabled(1L, false, "alice");
        verify(ruleDao).updateEnabled(1L, false);
    }

    private static NotificationRule rule(long id, AlarmSource source, String service, String resource,
            SeverityLevel severity, String tags) {
        return new NotificationRule(id, "rule-" + id, source, service, resource, severity, tags,
                "[\"EMAIL\"]", true, LocalDateTime.now(), LocalDateTime.now());
    }
}
