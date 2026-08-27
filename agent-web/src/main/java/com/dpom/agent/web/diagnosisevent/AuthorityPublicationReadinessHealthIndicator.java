package com.dpom.agent.web.diagnosisevent;

import com.dpom.agent.core.persistence.authority.AuthorityTerminalDao;
import com.dpom.agent.web.config.DiagnosisEventProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/** 只暴露权威 Outbox 的有界计数，不暴露事件正文、证据或凭据。 */
public final class AuthorityPublicationReadinessHealthIndicator implements HealthIndicator {

    private final AuthorityTerminalDao dao;
    private final int maxBacklog;

    /** 创建容量就绪检查。 */
    public AuthorityPublicationReadinessHealthIndicator(AuthorityTerminalDao dao,
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
