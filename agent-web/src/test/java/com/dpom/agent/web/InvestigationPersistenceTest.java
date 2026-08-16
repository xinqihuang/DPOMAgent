package com.dpom.agent.web;

import com.dpom.agent.core.hypothesis.Hypothesis;
import com.dpom.agent.core.hypothesis.HypothesisStatus;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.investigation.InvestigationStep;
import com.dpom.agent.core.observation.Observation;
import com.dpom.agent.core.persistence.HypothesisDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.InvestigationRunDao;
import com.dpom.agent.core.persistence.InvestigationStepDao;
import com.dpom.agent.core.persistence.ObservationDao;
import com.dpom.agent.core.persistence.command.HypothesisInsert;
import com.dpom.agent.core.persistence.command.IncidentInsert;
import com.dpom.agent.core.persistence.command.InvestigationInsert;
import com.dpom.agent.core.persistence.command.InvestigationRunInsert;
import com.dpom.agent.core.persistence.command.InvestigationStepInsert;
import com.dpom.agent.core.persistence.command.ObservationInsert;
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
        IncidentInsert incidentCommand = new IncidentInsert("asset-service", "prod", "1.2.3", "abc123def456",
                "创建设备成功但数据库无记录");
        incidentDao.insert(incidentCommand);
        long incidentId = incidentCommand.getId();
        assertThat(incidentId).isGreaterThan(0);

        InvestigationInsert investigationCommand = new InvestigationInsert(incidentId, InvestigationStatus.CREATED,
                null, 50, 100, 1800, 5);
        investigationDao.insert(investigationCommand);
        long investigationId = investigationCommand.getId();
        assertThat(investigationId).isGreaterThan(0);

        InvestigationRunInsert runCommand = new InvestigationRunInsert(investigationId, "gpt-4.1", "v1", "v1");
        runDao.insert(runCommand);
        long runId = runCommand.getId();
        assertThat(runId).isGreaterThan(0);

        InvestigationStepInsert step1 = new InvestigationStepInsert(investigationId, runId, 1, "RESEARCHING",
                "定位到 Repository", null);
        stepDao.append(step1);
        InvestigationStepInsert step2 = new InvestigationStepInsert(investigationId, runId, 2, "VALIDATING",
                "读取插入方法源码", null);
        stepDao.append(step2);

        HypothesisInsert hypothesisCommand = new HypothesisInsert(investigationId, null, "INSERT 后事务回滚",
                HypothesisStatus.PROPOSED, "需要日志证据");
        hypothesisDao.insert(hypothesisCommand);
        long hypothesisId = hypothesisCommand.getId();
        assertThat(hypothesisId).isGreaterThan(0);

        ObservationInsert observationCommand = new ObservationInsert(investigationId, runId, "codegraph",
                "src/AssetRepository.java", "AssetRepository.insert", String.valueOf(hypothesisId), null,
                "insert 方法存在且被 Service 调用", null);
        observationDao.insert(observationCommand);

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
