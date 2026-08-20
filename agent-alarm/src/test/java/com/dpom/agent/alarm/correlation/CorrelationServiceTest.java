package com.dpom.agent.alarm.correlation;

import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.AlarmIncidentDao;
import com.dpom.agent.alarm.persistence.command.AlarmIncidentInsert;
import com.dpom.agent.common.alarm.AlarmIncidentTriggerPort;
import com.dpom.agent.common.alarm.AlarmIncidentTriggerRequest;
import com.dpom.agent.common.alarm.AlarmIncidentTriggerResult;
import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * 关联编排服务单测：持久化、触发端口调用、端口未装配安全降级、端到端 stub。
 */
@ExtendWith(MockitoExtension.class)
class CorrelationServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 8, 19, 10, 0);

    @Mock
    private AlarmIncidentDao incidentDao;
    @Mock
    private AlarmAuditDao auditDao;

    @Test
    void persistsIncidentsAndMembersWithoutTriggeringForNonCritical() {
        AlarmCorrelationEngine engine = new AlarmCorrelationEngine(10, new StaticTopologySource());
        CorrelationService svc = new CorrelationService(engine, incidentDao, auditDao, Optional.empty());
        stubInsertIds();

        List<Long> ids = svc.correlateAndPersist(List.of(
                alarm(1L, "svc", "res-1", SeverityLevel.WARNING, AlarmStatus.FIRING, T0),
                alarm(2L, "svc", "res-2", SeverityLevel.WARNING, AlarmStatus.FIRING, T0)));

        assertThat(ids).hasSize(2);
        verify(incidentDao, org.mockito.Mockito.times(2)).addMember(anyLong(), anyLong());
    }

    @Test
    void safeDegradesWhenPortAbsentForCriticalIncident() {
        StaticTopologySource topology = new StaticTopologySource();
        topology.replaceAdjacency(java.util.Map.of("res-1", java.util.Set.of("res-2")));
        AlarmCorrelationEngine engine = new AlarmCorrelationEngine(10, topology);
        CorrelationService svc = new CorrelationService(engine, incidentDao, auditDao, Optional.empty());
        stubInsertIds();

        svc.correlateAndPersist(List.of(
                alarm(1L, "svc", "res-1", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0),
                alarm(2L, "svc", "res-2", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0)));

        verify(auditDao, org.mockito.Mockito.atLeastOnce()).insert(any());
    }

    @Test
    void triggersPortForCriticalIncident() {
        StaticTopologySource topology = new StaticTopologySource();
        topology.replaceAdjacency(java.util.Map.of("res-1", java.util.Set.of("res-2")));
        AlarmCorrelationEngine engine = new AlarmCorrelationEngine(10, topology);
        AtomicLong triggerCount = new AtomicLong();
        AlarmIncidentTriggerPort port = req -> {
            triggerCount.incrementAndGet();
            assertThat(req.severity()).isEqualTo(SeverityLevel.CRITICAL);
            assertThat(req.memberAlarmIds()).hasSize(2);
            return AlarmIncidentTriggerResult.triggered(9001L);
        };
        CorrelationService svc = new CorrelationService(engine, incidentDao, auditDao, Optional.of(port));
        stubInsertIds();

        svc.correlateAndPersist(List.of(
                alarm(1L, "svc", "res-1", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0),
                alarm(2L, "svc", "res-2", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0)));

        assertThat(triggerCount.get()).isEqualTo(1);
    }

    @Test
    void endToEndWithStubAgentCorePort() {
        StaticTopologySource topology = new StaticTopologySource();
        topology.replaceAdjacency(java.util.Map.of("res-1", java.util.Set.of("res-2")));
        AlarmCorrelationEngine engine = new AlarmCorrelationEngine(10, topology);
        StubTriggerPort port = new StubTriggerPort();
        CorrelationService svc = new CorrelationService(engine, incidentDao, auditDao, Optional.of(port));
        stubInsertIds();

        List<Long> ids = svc.correlateAndPersist(List.of(
                alarm(1L, "svc", "res-1", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0),
                alarm(2L, "svc", "res-2", SeverityLevel.WARNING, AlarmStatus.FIRING, T0.plusMinutes(1))));

        assertThat(ids).hasSize(1);
        assertThat(port.lastRequest().incidentId()).isEqualTo(ids.get(0));
        assertThat(port.lastRequest().memberAlarmIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(port.lastResult().triggered()).isTrue();
    }

    private void stubInsertIds() {
        AtomicLong idSeq = new AtomicLong(100);
        doAnswer(inv -> {
            AlarmIncidentInsert cmd = inv.getArgument(0);
            cmd.setId(idSeq.incrementAndGet());
            return 1;
        }).when(incidentDao).insert(any(AlarmIncidentInsert.class));
    }

    private static Alarm alarm(long id, String service, String resource, SeverityLevel severity,
            AlarmStatus status, LocalDateTime occurred) {
        return new Alarm(id, AlarmSource.AOM, "webhook", null, "fp-" + id, resource, "name-" + id,
                severity, status, 1, occurred, occurred, occurred, service, "prod", "{}", null);
    }

    /** 端到端 stub：模拟 agent-core 触发端口实现。 */
    private static final class StubTriggerPort implements AlarmIncidentTriggerPort {
        private AlarmIncidentTriggerRequest lastRequest;
        private AlarmIncidentTriggerResult lastResult;

        @Override
        public AlarmIncidentTriggerResult trigger(AlarmIncidentTriggerRequest request) {
            this.lastRequest = request;
            this.lastResult = AlarmIncidentTriggerResult.triggered(7777L);
            return lastResult;
        }

        AlarmIncidentTriggerRequest lastRequest() {
            return lastRequest;
        }

        AlarmIncidentTriggerResult lastResult() {
            return lastResult;
        }
    }
}
