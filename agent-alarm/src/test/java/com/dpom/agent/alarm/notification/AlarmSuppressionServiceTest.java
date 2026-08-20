package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.domain.AlarmSuppression;
import com.dpom.agent.alarm.domain.SuppressionKind;
import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.AlarmSuppressionDao;
import com.dpom.agent.alarm.persistence.command.AlarmSuppressionInsert;
import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 抑制/静默服务与通知编排单测：覆盖创建审计、窗口内抑制、窗口外放行、编排跳过。
 */
@ExtendWith(MockitoExtension.class)
class AlarmSuppressionServiceTest {

    @Mock
    private AlarmSuppressionDao suppressionDao;
    @Mock
    private AlarmAuditDao auditDao;

    @Test
    void createSuppressionInsertsAndAudits() {
        doAnswer(inv -> {
            inv.getArgument(0, AlarmSuppressionInsert.class).setId(1L);
            return 1;
        }).when(suppressionDao).insert(any());
        AlarmSuppressionService svc = new AlarmSuppressionService(suppressionDao, auditDao);

        long id = svc.createSuppression(SuppressionKind.SILENCE, "svc|res", "维护", LocalDateTime.now(),
                LocalDateTime.now().plusHours(1), "alice");

        assertThat(id).isEqualTo(1L);
        verify(auditDao).insert(any());
    }

    @Test
    void isSuppressedReturnsTrueWhenActiveWindowExists() {
        when(suppressionDao.findActiveByMatchKey(any(), any())).thenReturn(List.of(
                new AlarmSuppression(1L, SuppressionKind.SILENCE, "svc|res", "维护",
                        LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(1), "alice",
                        LocalDateTime.now())));
        AlarmSuppressionService svc = new AlarmSuppressionService(suppressionDao, auditDao);

        assertThat(svc.isSuppressed("svc|res")).isTrue();
    }

    @Test
    void isSuppressedReturnsFalseWhenNoActiveWindow() {
        when(suppressionDao.findActiveByMatchKey(any(), any())).thenReturn(List.of());
        AlarmSuppressionService svc = new AlarmSuppressionService(suppressionDao, auditDao);

        assertThat(svc.isSuppressed("svc|res")).isFalse();
    }

    @Test
    void isSuppressedReturnsFalseForNullKey() {
        AlarmSuppressionService svc = new AlarmSuppressionService(suppressionDao, auditDao);
        assertThat(svc.isSuppressed(null)).isFalse();
    }

    @Test
    void orchestratorSkipsWhenSuppressed() {
        AlarmSuppressionService suppression = new AlarmSuppressionService(suppressionDao, auditDao);
        NotificationRuleMatcher matcher = new NotificationRuleMatcher(
                org.mockito.Mockito.mock(com.dpom.agent.alarm.persistence.NotificationRuleDao.class));
        NotificationDispatchService dispatch = new NotificationDispatchService(List.of(),
                org.mockito.Mockito.mock(com.dpom.agent.alarm.persistence.NotificationRecordDao.class),
                new com.fasterxml.jackson.databind.ObjectMapper());
        when(suppressionDao.findActiveByMatchKey(any(), any())).thenReturn(List.of(
                new AlarmSuppression(1L, SuppressionKind.SUPPRESSION, "svc|res", null,
                        LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1), "alice",
                        LocalDateTime.now())));
        NotificationOrchestrator orchestrator = new NotificationOrchestrator(suppression, matcher, dispatch,
                auditDao);

        orchestrator.notify(7L, new NotificationMatchInput(AlarmSource.AOM, "svc", "res",
                SeverityLevel.CRITICAL, java.util.Map.of()), "主题", "正文");

        verify(auditDao).insert(any());
    }
}
