package com.dpom.agent.core.alarm;

import com.dpom.agent.common.alarm.AlarmIncidentTriggerRequest;
import com.dpom.agent.common.alarm.AlarmIncidentTriggerResult;
import com.dpom.agent.common.alarm.SeverityLevel;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.command.IncidentInsert;
import com.dpom.agent.core.persistence.command.InvestigationInsert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 告警事件触发适配器单测：验证 Incident/Investigation 落库与触发结果。
 */
@ExtendWith(MockitoExtension.class)
class AlarmIncidentTriggerAdapterTest {

    @Mock
    private IncidentDao incidentDao;

    @Mock
    private InvestigationDao investigationDao;

    private AlarmIncidentTriggerAdapter adapter;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(txManager.getTransaction(any())).thenReturn(status);
        adapter = new AlarmIncidentTriggerAdapter(incidentDao, investigationDao, txManager);
    }

    @Test
    void triggerCreatesIncidentAndInvestigationAndReturnsTriggered() {
        doAnswer(inv -> {
            IncidentInsert cmd = inv.getArgument(0);
            cmd.setId(101L);
            return 1;
        }).when(incidentDao).insert(any(IncidentInsert.class));
        doAnswer(inv -> {
            InvestigationInsert cmd = inv.getArgument(0);
            cmd.setId(202L);
            return 1;
        }).when(investigationDao).insert(any(InvestigationInsert.class));

        AlarmIncidentTriggerRequest request = new AlarmIncidentTriggerRequest(7L, "asset-service", "prod",
                SeverityLevel.CRITICAL, "数据库无记录", List.of(1L, 2L), LocalDateTime.now());
        AlarmIncidentTriggerResult result = adapter.trigger(request);

        assertThat(result.triggered()).isTrue();
        assertThat(result.investigationId()).isEqualTo(202L);
        assertThat(result.skipReason()).isNull();
    }
}
