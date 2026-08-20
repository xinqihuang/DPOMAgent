package com.dpom.agent.alarm.correlation;

import com.dpom.agent.alarm.domain.AlarmIncident;
import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.AlarmIncidentDao;
import com.dpom.agent.common.alarm.AlarmIncidentStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 事件生命周期服务单测：状态流转与升级评估。
 */
@ExtendWith(MockitoExtension.class)
class AlarmIncidentLifecycleServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 8, 19, 10, 0);

    @Mock
    private AlarmIncidentDao incidentDao;
    @Mock
    private AlarmAuditDao auditDao;

    @Test
    void acknowledgeTransitionsOpenToAcknowledgedAndAudits() {
        when(incidentDao.findById(1L)).thenReturn(Optional.of(incident(AlarmIncidentStatus.OPEN, T0)));
        AlarmIncidentLifecycleService svc = new AlarmIncidentLifecycleService(incidentDao, auditDao, 30);

        svc.acknowledge(1L, "alice");

        verify(incidentDao).updateLifecycle(anyLong(), any(), any(), any(), any(), any(), any());
        verify(auditDao).insert(any());
    }

    @Test
    void acknowledgeRejectsNonOpen() {
        when(incidentDao.findById(1L)).thenReturn(Optional.of(incident(AlarmIncidentStatus.ACKNOWLEDGED, T0)));
        AlarmIncidentLifecycleService svc = new AlarmIncidentLifecycleService(incidentDao, auditDao, 30);

        assertThatThrownBy(() -> svc.acknowledge(1L, "alice")).isInstanceOf(IllegalStateException.class);
        verify(incidentDao, never()).updateLifecycle(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void resolveTransitionsAcknowledgedToResolved() {
        when(incidentDao.findById(1L)).thenReturn(Optional.of(incident(AlarmIncidentStatus.ACKNOWLEDGED, T0)));
        AlarmIncidentLifecycleService svc = new AlarmIncidentLifecycleService(incidentDao, auditDao, 30);

        svc.resolve(1L);

        verify(incidentDao).updateLifecycle(anyLong(), any(), any(), any(), any(), any(), any());
        verify(auditDao).insert(any());
    }

    @Test
    void resolveRejectsAlreadyResolved() {
        when(incidentDao.findById(1L)).thenReturn(Optional.of(incident(AlarmIncidentStatus.RESOLVED, T0)));
        AlarmIncidentLifecycleService svc = new AlarmIncidentLifecycleService(incidentDao, auditDao, 30);

        assertThatThrownBy(() -> svc.resolve(1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void evaluateEscalationMarksWhenOpenBeyondTimeout() {
        LocalDateTime oldStart = LocalDateTime.now().minusMinutes(60);
        when(incidentDao.findById(1L)).thenReturn(Optional.of(incident(AlarmIncidentStatus.OPEN, oldStart)));
        AlarmIncidentLifecycleService svc = new AlarmIncidentLifecycleService(incidentDao, auditDao, 30);

        boolean escalated = svc.evaluateEscalation(1L);

        assertThat(escalated).isTrue();
        verify(incidentDao).updateEscalation(anyLong(), anyBoolean(), any(), any());
        verify(auditDao).insert(any());
    }

    @Test
    void evaluateEscalationSkipsAcknowledged() {
        when(incidentDao.findById(1L)).thenReturn(Optional.of(incident(AlarmIncidentStatus.ACKNOWLEDGED, T0)));
        AlarmIncidentLifecycleService svc = new AlarmIncidentLifecycleService(incidentDao, auditDao, 30);

        assertThat(svc.evaluateEscalation(1L)).isFalse();
        verify(incidentDao, never()).updateEscalation(anyLong(), anyBoolean(), any(), any());
    }

    @Test
    void evaluateEscalationSkipsWithinTimeout() {
        LocalDateTime recent = LocalDateTime.now().minusMinutes(5);
        when(incidentDao.findById(1L)).thenReturn(Optional.of(incident(AlarmIncidentStatus.OPEN, recent)));
        AlarmIncidentLifecycleService svc = new AlarmIncidentLifecycleService(incidentDao, auditDao, 30);

        assertThat(svc.evaluateEscalation(1L)).isFalse();
        verify(incidentDao, never()).updateEscalation(anyLong(), anyBoolean(), any(), any());
    }

    private static AlarmIncident incident(AlarmIncidentStatus status, LocalDateTime startedAt) {
        return new AlarmIncident(1L, status, SeverityLevel.CRITICAL, "svc", "prod",
                "SINGLE", "summary", startedAt, null, false, null, null, null, null, null, null);
    }
}
