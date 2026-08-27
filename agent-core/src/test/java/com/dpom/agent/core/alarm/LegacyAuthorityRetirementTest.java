package com.dpom.agent.core.alarm;

import com.dpom.agent.common.alarm.AlarmIncidentTriggerRequest;
import com.dpom.agent.common.alarm.SeverityLevel;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** 旧源权威退役后拒绝告警创建，但不删除历史数据。 */
class LegacyAuthorityRetirementTest {
    @Test
    void disabledAdmissionDoesNotWriteLegacyAggregate() {
        IncidentDao incidents = mock(IncidentDao.class);
        InvestigationDao investigations = mock(InvestigationDao.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        var adapter = new AlarmIncidentTriggerAdapter(incidents, investigations, transactions, false);

        var result = adapter.trigger(new AlarmIncidentTriggerRequest(
                1L, "svc", "prod", SeverityLevel.CRITICAL, "summary", java.util.List.of(1L),
                java.time.LocalDateTime.now()));

        assertThat(result.triggered()).isFalse();
        assertThat(result.skipReason()).isEqualTo("LEGACY_AUTHORITY_RETIRED");
        verifyNoInteractions(incidents, investigations, transactions);
    }
}
