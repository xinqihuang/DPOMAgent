package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 告警标准化器单测：覆盖各来源无损投影、严重度映射与未知/ malformed 拒绝。
 */
class AlarmNormalizerTest {

    private static final String AOM_EVENT = """
            {"id":"aom-1","resource":"res-1","name":"CPU 高","severity":"Major",
             "status":"FIRING","occurredAt":"2026-08-19T10:00:00","service":"svc","environment":"prod",
             "tags":"k=v","extra":"保留"}""";

    @Test
    void aomNormalizerLosslessProjectionAndSeverity() {
        NormalizedAlarm result = new AomAlarmNormalizer().normalize(AOM_EVENT).orElseThrow();
        assertThat(result.source()).isEqualTo(AlarmSource.AOM);
        assertThat(result.externalId()).isEqualTo("aom-1");
        assertThat(result.resourceId()).isEqualTo("res-1");
        assertThat(result.alarmName()).isEqualTo("CPU 高");
        assertThat(result.severity()).isEqualTo(SeverityLevel.CRITICAL);
        assertThat(result.status()).isEqualTo(AlarmStatus.FIRING);
        assertThat(result.rawPayload()).isEqualTo(AOM_EVENT);
    }

    @Test
    void apmNormalizerMapsFatalToCritical() {
        String event = "{\"resource\":\"r\",\"name\":\"n\",\"severity\":\"Fatal\","
                + "\"occurredAt\":\"2026-08-19T10:00:00\"}";
        NormalizedAlarm result = new ApmAlarmNormalizer().normalize(event).orElseThrow();
        assertThat(result.severity()).isEqualTo(SeverityLevel.CRITICAL);
        assertThat(result.status()).isEqualTo(AlarmStatus.FIRING);
    }

    @Test
    void cesNormalizerMapsWarning() {
        String event = "{\"resource\":\"r\",\"name\":\"n\",\"severity\":\"Warning\","
                + "\"occurredAt\":\"2026-08-19T10:00:00\"}";
        NormalizedAlarm result = new CesAlarmNormalizer().normalize(event).orElseThrow();
        assertThat(result.severity()).isEqualTo(SeverityLevel.WARNING);
    }

    @Test
    void ltsNormalizerMapsInfo() {
        String event = "{\"resource\":\"r\",\"name\":\"n\",\"severity\":\"Info\","
                + "\"occurredAt\":\"2026-08-19T10:00:00\"}";
        NormalizedAlarm result = new LtsAlarmNormalizer().normalize(event).orElseThrow();
        assertThat(result.severity()).isEqualTo(SeverityLevel.INFO);
    }

    @Test
    void resolvedStatusMapped() {
        String event = "{\"resource\":\"r\",\"name\":\"n\",\"severity\":\"Error\",\"status\":\"RESOLVED\","
                + "\"occurredAt\":\"2026-08-19T10:00:00\"}";
        NormalizedAlarm result = new LtsAlarmNormalizer().normalize(event).orElseThrow();
        assertThat(result.status()).isEqualTo(AlarmStatus.RESOLVED);
    }

    @Test
    void missingRequiredFieldRejected() {
        String event = "{\"resource\":\"r\",\"occurredAt\":\"2026-08-19T10:00:00\"}";
        Optional<NormalizedAlarm> result = new AomAlarmNormalizer().normalize(event);
        assertThat(result).isEmpty();
    }

    @Test
    void malformedJsonRejected() {
        Optional<NormalizedAlarm> result = new AomAlarmNormalizer().normalize("not-json");
        assertThat(result).isEmpty();
    }

    @Test
    void blankPayloadRejected() {
        Optional<NormalizedAlarm> result = new AomAlarmNormalizer().normalize("  ");
        assertThat(result).isEmpty();
    }

    @Test
    void registryRejectsUnknownSource() {
        AlarmNormalizerRegistry registry = new AlarmNormalizerRegistry(List.of(
                new AomAlarmNormalizer(), new CesAlarmNormalizer(),
                new ApmAlarmNormalizer(), new LtsAlarmNormalizer()));
        assertThat(registry.get(AlarmSource.AOM)).isPresent();
        assertThat(registry.get(AlarmSource.CES)).isPresent();
        assertThat(registry.get(AlarmSource.APM)).isPresent();
        assertThat(registry.get(AlarmSource.LTS)).isPresent();
    }
}
