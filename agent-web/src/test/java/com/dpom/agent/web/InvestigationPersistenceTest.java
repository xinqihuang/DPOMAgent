package com.dpom.agent.web;

import com.dpom.agent.core.hypothesis.Hypothesis;
import com.dpom.agent.core.hypothesis.HypothesisStatus;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationRun;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.investigation.InvestigationStep;
import com.dpom.agent.core.observation.Observation;
import com.dpom.agent.core.persistence.HypothesisDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.InvestigationRunDao;
import com.dpom.agent.core.persistence.InvestigationStepDao;
import com.dpom.agent.core.persistence.ObservationDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Investigation 持久化集成测试：验证空库迁移、完整链路落库与重启可恢复。
 */
@SpringBootTest
class InvestigationPersistenceTest {

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    @Autowired
    private InvestigationRunDao runDao;

    @Autowired
    private InvestigationStepDao stepDao;

    @Autowired
    private ObservationDao observationDao;

    @Autowired
    private HypothesisDao hypothesisDao;

    @Autowired
    private DataSource dataSource;

    /**
     * 验证空库迁移成功，并能创建 Investigation→Run→Step→Observation→Hypothesis 链路，模拟重启后仍可恢复。
     */
    @Test
    void migrateAndPersistRecoverableChain() {
        // 1) Flyway 已在上下文启动时对空库执行 V1 迁移，校验迁移历史存在。
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);

        // 2) 创建 Incident → Investigation → Run → Step → Hypothesis → Observation。
        long incidentId = incidentDao.insert(new Incident(
                null, "asset-service", "prod", "1.2.3", "abc123def456", "创建设备成功但数据库无记录", null));
        assertThat(incidentId).isGreaterThan(0);

        long investigationId = investigationDao.insert(new Investigation(
                null, incidentId, InvestigationStatus.CREATED, null, 50, 100, 1800, 5, null, null));
        assertThat(investigationId).isGreaterThan(0);

        long runId = runDao.insert(new InvestigationRun(
                null, investigationId, "gpt-4.1", "v1", "v1", null, null));
        assertThat(runId).isGreaterThan(0);

        stepDao.append(new InvestigationStep(null, investigationId, runId, 1, "RESEARCHING", "定位到 Repository", null, null));
        stepDao.append(new InvestigationStep(null, investigationId, runId, 2, "VALIDATING", "读取插入方法源码", null, null));

        long hypothesisId = hypothesisDao.insert(new Hypothesis(
                null, investigationId, null, "INSERT 后事务回滚", HypothesisStatus.PROPOSED, "需要日志证据", null, null));
        assertThat(hypothesisId).isGreaterThan(0);

        observationDao.insert(new Observation(
                null, investigationId, runId, "codegraph", "src/AssetRepository.java",
                "AssetRepository.insert", String.valueOf(hypothesisId), null,
                "insert 方法存在且被 Service 调用", null, null));

        // 3) 校验回读。
        Investigation investigation = investigationDao.findById(investigationId).orElseThrow();
        assertThat(investigation.status()).isEqualTo(InvestigationStatus.CREATED);
        assertThat(investigation.incidentId()).isEqualTo(incidentId);
        assertThat(investigation.createdAt()).isNotNull();

        List<InvestigationStep> steps = stepDao.findByInvestigationId(investigationId);
        assertThat(steps).hasSize(2);
        assertThat(steps).extracting(InvestigationStep::stepOrder).containsExactly(1, 2);

        Hypothesis hypothesis = hypothesisDao.findById(hypothesisId).orElseThrow();
        assertThat(hypothesis.status()).isEqualTo(HypothesisStatus.PROPOSED);

        List<Observation> observations = observationDao.findByInvestigationId(investigationId);
        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).source()).isEqualTo("codegraph");
        assertThat(observations.get(0).location()).isEqualTo("AssetRepository.insert");
        assertThat(observations.get(0).supportsHypothesisIds()).isEqualTo(String.valueOf(hypothesisId));

        // 4) 状态变更并模拟重启：通过新的连接直接读库，证明状态持久化于 DB 而非进程内会话。
        investigationDao.updateStatus(investigationId, InvestigationStatus.RESEARCHING);
        hypothesisDao.updateStatus(hypothesisId, HypothesisStatus.VALIDATED);

        JdbcTemplate fresh = new JdbcTemplate(dataSource);
        String persistedStatus = fresh.queryForObject(
                "SELECT status FROM investigation WHERE id = ?", String.class, investigationId);
        assertThat(persistedStatus).isEqualTo("RESEARCHING");

        String persistedHypothesisStatus = fresh.queryForObject(
                "SELECT status FROM hypothesis WHERE id = ?", String.class, hypothesisId);
        assertThat(persistedHypothesisStatus).isEqualTo("VALIDATED");

        // 重启后从 DAO 恢复同一状态（等价于新进程重新加载）。
        Investigation recovered = investigationDao.findById(investigationId).orElseThrow();
        assertThat(recovered.status()).isEqualTo(InvestigationStatus.RESEARCHING);
        assertThat(recovered.currentRunId()).isNull();
    }
}
