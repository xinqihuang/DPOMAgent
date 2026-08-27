package com.dpom.agent.web.diagnosisprogress;

import com.dpom.agent.core.persistence.authority.AuthorityProgressDao;
import com.dpom.agent.web.config.DiagnosisEventProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/** 仅暴露低基数 Progress Outbox 状态，不读取或暴露正文。 */
public final class AuthorityProgressReadinessHealthIndicator implements HealthIndicator {

    private final AuthorityProgressDao dao;
    private final int maxBacklog;

    public AuthorityProgressReadinessHealthIndicator(AuthorityProgressDao dao,
            DiagnosisEventProperties properties) {
        this.dao = dao;
        this.maxBacklog = properties.getDelivery().getMaxBacklog();
    }

    @Override
    public Health health() {
        int pending = dao.countIntentsByStatus("PENDING");
        int inFlight = dao.countIntentsByStatus("IN_FLIGHT");
        int dead = dao.countIntentsByStatus("DEAD");
        Health.Builder result = pending + inFlight > maxBacklog ? Health.outOfService() : Health.up();
        return result.withDetail("pending", pending).withDetail("inFlight", inFlight)
                .withDetail("dead", dead).withDetail("capacity", maxBacklog).build();
    }
}
