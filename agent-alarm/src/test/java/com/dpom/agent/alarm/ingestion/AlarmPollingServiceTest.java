package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.common.alarm.AlarmSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警轮询服务单测：验证增量拉取、接入与游标推进。
 */
@ExtendWith(MockitoExtension.class)
class AlarmPollingServiceTest {

    @Mock
    private AlarmSourceGateway gateway;

    @Mock
    private AlarmIngestionService ingestionService;

    @Test
    void pollOnceIngestsEventsAndAdvancesCursor() {
        AlarmPollingService service = new AlarmPollingService(gateway, ingestionService, 100, true);
        LocalDateTime t1 = LocalDateTime.of(2026, 8, 19, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 8, 19, 10, 5);
        when(gateway.fetchSince(eq(AlarmSource.AOM), any(), eq(100)))
                .thenReturn(List.of(new RawAlarmEvent("e1", t1, "{}"), new RawAlarmEvent("e2", t2, "{}")));

        int count = service.pollOnce(AlarmSource.AOM);

        assertThat(count).isEqualTo(2);
        verify(ingestionService, times(2)).ingest(eq(AlarmSource.AOM), eq("{}"), eq("poll"));
    }

    @Test
    void secondPollUsesAdvancedCursor() {
        AlarmPollingService service = new AlarmPollingService(gateway, ingestionService, 100, true);
        LocalDateTime t1 = LocalDateTime.of(2026, 8, 19, 10, 0);
        when(gateway.fetchSince(eq(AlarmSource.CES), any(), eq(100)))
                .thenReturn(List.of(new RawAlarmEvent("e1", t1, "{}")))
                .thenReturn(List.of());

        assertThat(service.pollOnce(AlarmSource.CES)).isEqualTo(1);
        assertThat(service.pollOnce(AlarmSource.CES)).isEqualTo(0);
        verify(gateway).fetchSince(eq(AlarmSource.CES), isNull(), eq(100));
        verify(gateway).fetchSince(eq(AlarmSource.CES), eq(t1), eq(100));
    }

    @Test
    void pollAllNoopWhenDisabled() {
        AlarmPollingService service = new AlarmPollingService(gateway, ingestionService, 100, false);
        service.pollAll();
        verify(gateway, times(0)).fetchSince(any(), any(), anyInt());
    }
}
