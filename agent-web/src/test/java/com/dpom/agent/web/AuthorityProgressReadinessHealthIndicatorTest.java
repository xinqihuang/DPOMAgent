package com.dpom.agent.web;

import com.dpom.agent.core.persistence.authority.AuthorityProgressDao;
import com.dpom.agent.web.config.DiagnosisEventProperties;
import com.dpom.agent.web.diagnosisprogress.AuthorityProgressReadinessHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Progress readiness 仅暴露容量计数并在积压越界时 fail closed。 */
class AuthorityProgressReadinessHealthIndicatorTest {

    @Test
    void reportsOnlyBoundedCountsAndFailsWhenActiveBacklogExceedsCapacity() {
        AuthorityProgressDao dao = mock(AuthorityProgressDao.class);
        when(dao.countIntentsByStatus("PENDING")).thenReturn(8);
        when(dao.countIntentsByStatus("IN_FLIGHT")).thenReturn(3);
        when(dao.countIntentsByStatus("DEAD")).thenReturn(2);
        DiagnosisEventProperties properties = new DiagnosisEventProperties();
        properties.getDelivery().setMaxBacklog(10);

        var health = new AuthorityProgressReadinessHealthIndicator(dao, properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "pending", 8, "inFlight", 3, "dead", 2, "capacity", 10));
        assertThat(health.getDetails().toString()).doesNotContain("canonical", "evidence", "secret");
    }
}
