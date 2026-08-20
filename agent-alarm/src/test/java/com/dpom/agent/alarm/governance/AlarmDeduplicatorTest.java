package com.dpom.agent.alarm.governance;

import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.ingestion.NormalizedAlarm;
import com.dpom.agent.alarm.persistence.AlarmDao;
import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警去重器单测：覆盖窗内合并、超窗新建与缓存快速路径。
 */
@ExtendWith(MockitoExtension.class)
class AlarmDeduplicatorTest {

    @Mock
    private AlarmDao alarmDao;

    @Mock
    private AlarmFingerprintCache cache;

    @Test
    void withinWindowMerges() {
        AlarmDeduplicator dedup = new AlarmDeduplicator(alarmDao, cache, 5);
        LocalDateTime existingLast = LocalDateTime.of(2026, 8, 19, 10, 0);
        LocalDateTime newOccurred = LocalDateTime.of(2026, 8, 19, 10, 3);
        when(cache.existingId(any())).thenReturn(Optional.empty());
        when(alarmDao.findLatestByFingerprint("fp")).thenReturn(Optional.of(alarm(1L, existingLast)));

        DedupDecision decision = dedup.evaluate(normalized(newOccurred), "fp");

        assertThat(decision.merge()).isTrue();
        assertThat(decision.existingAlarmId()).isEqualTo(1L);
    }

    @Test
    void outsideWindowCreatesNew() {
        AlarmDeduplicator dedup = new AlarmDeduplicator(alarmDao, cache, 5);
        LocalDateTime existingLast = LocalDateTime.of(2026, 8, 19, 10, 0);
        LocalDateTime newOccurred = LocalDateTime.of(2026, 8, 19, 10, 10);
        when(cache.existingId(any())).thenReturn(Optional.empty());
        when(alarmDao.findLatestByFingerprint("fp")).thenReturn(Optional.of(alarm(1L, existingLast)));

        DedupDecision decision = dedup.evaluate(normalized(newOccurred), "fp");

        assertThat(decision.merge()).isFalse();
    }

    @Test
    void cacheFastPathMergesWithoutFingerprintScan() {
        AlarmDeduplicator dedup = new AlarmDeduplicator(alarmDao, cache, 5);
        LocalDateTime existingLast = LocalDateTime.of(2026, 8, 19, 10, 0);
        when(cache.existingId("fp")).thenReturn(Optional.of(7L));
        when(alarmDao.findById(7L)).thenReturn(Optional.of(alarm(7L, existingLast)));

        DedupDecision decision = dedup.evaluate(normalized(LocalDateTime.of(2026, 8, 19, 10, 2)), "fp");

        assertThat(decision.merge()).isTrue();
        assertThat(decision.existingAlarmId()).isEqualTo(7L);
        verify(alarmDao, never()).findLatestByFingerprint(any());
    }

    private static Alarm alarm(long id, LocalDateTime lastOccurred) {
        return new Alarm(id, AlarmSource.AOM, "webhook", null, "fp", "res", "name", SeverityLevel.CRITICAL,
                AlarmStatus.FIRING, 1, lastOccurred, lastOccurred, lastOccurred, "svc", "prod", "{}", null);
    }

    private static NormalizedAlarm normalized(LocalDateTime occurred) {
        return new NormalizedAlarm(AlarmSource.AOM, null, "res", "name", SeverityLevel.CRITICAL,
                AlarmStatus.FIRING, occurred, "svc", "prod", null, "{}");
    }
}
