package com.dpom.agent.alarm.correlation;

import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.domain.AlarmIncident;
import com.dpom.agent.common.alarm.AlarmIncidentStatus;
import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 确定性关联引擎单测：覆盖聚合/不聚合/不调 LLM。
 */
class AlarmCorrelationEngineTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 8, 19, 10, 0);

    @Test
    void aggregatesAdjacentAlarmsInWindow() {
        StaticTopologySource topology = new StaticTopologySource();
        topology.replaceAdjacency(Map.of("res-1", Set.of("res-2")));
        AlarmCorrelationEngine engine = new AlarmCorrelationEngine(10, topology);

        Alarm a = alarm(1L, "svc", "res-1", SeverityLevel.WARNING, AlarmStatus.FIRING, T0);
        Alarm b = alarm(2L, "svc", "res-2", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0.plusMinutes(2));

        List<CorrelatedIncident> results = engine.correlate(List.of(a, b));
        assertThat(results).hasSize(1);
        CorrelatedIncident ci = results.get(0);
        assertThat(ci.memberAlarmIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(ci.incident().severity()).isEqualTo(SeverityLevel.CRITICAL);
        assertThat(ci.incident().correlationBasis()).isEqualTo(AlarmCorrelationEngine.BASIS_AGGREGATE);
        assertThat(ci.incident().status()).isEqualTo(AlarmIncidentStatus.OPEN);
        assertThat(ci.incident().endedAt()).isNull();
    }

    @Test
    void doesNotAggregateAcrossTimeWindow() {
        StaticTopologySource topology = new StaticTopologySource();
        topology.replaceAdjacency(Map.of("res-1", Set.of("res-2")));
        AlarmCorrelationEngine engine = new AlarmCorrelationEngine(10, topology);

        Alarm a = alarm(1L, "svc", "res-1", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0);
        Alarm b = alarm(2L, "svc", "res-2", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0.plusMinutes(30));

        List<CorrelatedIncident> results = engine.correlate(List.of(a, b));
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(ci ->
                assertThat(ci.incident().correlationBasis()).isEqualTo(AlarmCorrelationEngine.BASIS_SINGLE));
    }

    @Test
    void doesNotAggregateNonAdjacentDifferentResources() {
        StaticTopologySource topology = new StaticTopologySource();
        AlarmCorrelationEngine engine = new AlarmCorrelationEngine(10, topology);

        Alarm a = alarm(1L, "svc", "res-1", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0);
        Alarm b = alarm(2L, "svc", "res-2", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0);

        List<CorrelatedIncident> results = engine.correlate(List.of(a, b));
        assertThat(results).hasSize(2);
    }

    @Test
    void sameResourceCorrelatesEvenWithoutTopology() {
        StaticTopologySource topology = new StaticTopologySource();
        AlarmCorrelationEngine engine = new AlarmCorrelationEngine(10, topology);

        Alarm a = alarm(1L, "svc", "res-1", SeverityLevel.WARNING, AlarmStatus.FIRING, T0);
        Alarm b = alarm(2L, "svc", "res-1", SeverityLevel.WARNING, AlarmStatus.FIRING, T0.plusMinutes(1));

        List<CorrelatedIncident> results = engine.correlate(List.of(a, b));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).memberAlarmIds()).hasSize(2);
    }

    @Test
    void doesNotAggregateAcrossDifferentServiceOrEnvironment() {
        StaticTopologySource topology = new StaticTopologySource();
        topology.replaceAdjacency(Map.of("res-1", Set.of("res-2")));
        AlarmCorrelationEngine engine = new AlarmCorrelationEngine(10, topology);

        Alarm a = alarm(1L, "svc-a", "res-1", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0);
        Alarm b = alarm(2L, "svc-b", "res-2", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0);
        Alarm c = alarm(3L, "svc-a", "res-1", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0);

        List<CorrelatedIncident> results = engine.correlate(List.of(a, b, c));
        assertThat(results).hasSize(2);
    }

    @Test
    void singleAlarmProducesSingleIncident() {
        AlarmCorrelationEngine engine = new AlarmCorrelationEngine(10, new StaticTopologySource());
        Alarm a = alarm(1L, "svc", "res-1", SeverityLevel.INFO, AlarmStatus.FIRING, T0);

        List<CorrelatedIncident> results = engine.correlate(List.of(a));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).incident().correlationBasis()).isEqualTo(AlarmCorrelationEngine.BASIS_SINGLE);
        assertThat(results.get(0).incident().severity()).isEqualTo(SeverityLevel.INFO);
    }

    @Test
    void resolvedAlarmsProduceEndedIncident() {
        AlarmCorrelationEngine engine = new AlarmCorrelationEngine(10, new StaticTopologySource());
        Alarm a = alarm(1L, "svc", "res-1", SeverityLevel.CRITICAL, AlarmStatus.RESOLVED, T0);
        Alarm b = alarm(2L, "svc", "res-1", SeverityLevel.CRITICAL, AlarmStatus.RESOLVED, T0.plusMinutes(2));

        List<CorrelatedIncident> results = engine.correlate(List.of(a, b));
        assertThat(results).hasSize(1);
        AlarmIncident incident = results.get(0).incident();
        assertThat(incident.endedAt()).isEqualTo(T0.plusMinutes(2));
        assertThat(incident.startedAt()).isEqualTo(T0);
    }

    @Test
    void engineIsDeterministicAndLlmFree() {
        StaticTopologySource topology = new StaticTopologySource();
        topology.replaceAdjacency(Map.of("res-1", Set.of("res-2")));
        AlarmCorrelationEngine engine = new AlarmCorrelationEngine(5, topology);
        Alarm a = alarm(1L, "svc", "res-1", SeverityLevel.WARNING, AlarmStatus.FIRING, T0);
        Alarm b = alarm(2L, "svc", "res-2", SeverityLevel.CRITICAL, AlarmStatus.FIRING, T0.plusMinutes(1));

        List<CorrelatedIncident> r1 = engine.correlate(List.of(a, b));
        List<CorrelatedIncident> r2 = engine.correlate(List.of(a, b));
        assertThat(r1).hasSize(1);
        assertThat(r1.get(0).incident().severity()).isEqualTo(SeverityLevel.CRITICAL);
        assertThat(r1.get(0).incident().summary()).isEqualTo(r2.get(0).incident().summary());
    }

    private static Alarm alarm(long id, String service, String resource, SeverityLevel severity,
            AlarmStatus status, LocalDateTime occurred) {
        return new Alarm(id, AlarmSource.AOM, "webhook", null, "fp-" + id, resource, "name-" + id,
                severity, status, 1, occurred, occurred, occurred, service, "prod", "{}", null);
    }
}
