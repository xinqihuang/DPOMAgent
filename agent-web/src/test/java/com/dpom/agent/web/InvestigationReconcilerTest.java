package com.dpom.agent.web;

import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.ApiRequestRecord;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationApiRequestDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.web.service.InvestigationReconciler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动 reconciliation：遗留非终态 -> FAILED，结论恰一次，api_request 同步 FAILED，重复调用幂等。
 */
@SpringBootTest
class InvestigationReconcilerTest {

    @Autowired private InvestigationReconciler reconciler;
    @Autowired private InvestigationDao investigationDao;
    @Autowired private ConclusionDao conclusionDao;
    @Autowired private InvestigationApiRequestDao apiRequestDao;
    @Autowired private IncidentDao incidentDao;
    @Autowired private JdbcClient jdbcClient;

    @Test
    void reconcileMarksLegacyNonTerminalFailedExactlyOnce() {
        long incidentId = incidentDao.insert(new Incident(null, "legacy-svc", "prod", "1.0.0", "abc1234",
                "legacy crash", null));
        long investigationId = investigationDao.insert(new Investigation(null, incidentId,
                InvestigationStatus.RESEARCHING, null, 30, 60, 1800, 5, null, null));
        apiRequestDao.insert("legacy-" + UUID.randomUUID(), "hash-legacy", investigationId, "SUBMITTED");

        reconciler.reconcile();

        Investigation inv = investigationDao.findById(investigationId).orElseThrow();
        assertThat(inv.status()).isEqualTo(InvestigationStatus.FAILED);
        Conclusion first = conclusionDao.findByInvestigationId(investigationId).orElseThrow();
        assertThat(first.resultType()).isEqualTo("FAILED");
        assertThat(first.summary()).contains("进程重启");
        ApiRequestRecord record = apiRequestDao.findByInvestigationId(investigationId).orElseThrow();
        assertThat(record.status()).isEqualTo("FAILED");
        assertThat(record.lastErrorCode()).isEqualTo("RECONCILED_AFTER_RESTART");

        reconciler.reconcile();
        Conclusion second = conclusionDao.findByInvestigationId(investigationId).orElseThrow();
        assertThat(second.id()).isEqualTo(first.id());
        long count = jdbcClient.sql("SELECT COUNT(*) FROM conclusion WHERE investigation_id = :id")
                .param("id", investigationId).query(Long.class).single();
        assertThat(count).isEqualTo(1);
    }
}
