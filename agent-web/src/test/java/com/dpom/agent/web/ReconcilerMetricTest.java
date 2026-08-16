package com.dpom.agent.web;

import com.dpom.agent.core.persistence.command.IncidentInsert;
import com.dpom.agent.core.persistence.command.ApiRequestInsert;
import com.dpom.agent.core.persistence.command.InvestigationInsert;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationApiRequestDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.web.service.InvestigationReconciler;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * reconciliation.recovered 计实际恢复数：空扫描/重复运行不递增。
 */
@SpringBootTest
class ReconcilerMetricTest {

    @Autowired private InvestigationReconciler reconciler;
    @Autowired private InvestigationDao investigationDao;
    @Autowired private InvestigationApiRequestDao apiRequestDao;
    @Autowired private IncidentDao incidentDao;
    @Autowired private MeterRegistry meterRegistry;

    @Test
    void recoveredCounterCountsActualRecoveriesOnly() {
        IncidentInsert incidentCommand = new IncidentInsert("legacy-svc", "prod", "1.0.0", "abc1234", "legacy");
        incidentDao.insert(incidentCommand);
        InvestigationInsert investigationCommand = new InvestigationInsert(incidentCommand.getId(),
                InvestigationStatus.RESEARCHING, null, 30, 60, 1800, 5);
        investigationDao.insert(investigationCommand);
        long investigationId = investigationCommand.getId();
        apiRequestDao.insert(new ApiRequestInsert("legacy-" + UUID.randomUUID(), "hash", investigationId, "SUBMITTED"));

        int nonTerminal = investigationDao.findNonTerminal().size();
        double before = counter();

        reconciler.reconcile();
        // 计数增量 == 实际被恢复的非终态数
        assertThat(counter() - before).isEqualTo((double) nonTerminal);

        // 重复运行：已无非终态，不再递增
        double afterFirst = counter();
        reconciler.reconcile();
        assertThat(counter()).isEqualTo(afterFirst);
    }

    private double counter() {
        io.micrometer.core.instrument.Counter counter = meterRegistry.find("dpom.reconciliation.recovered").counter();
        return counter == null ? 0.0 : counter.count();
    }
}
