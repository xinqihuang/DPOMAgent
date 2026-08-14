package com.dpom.agent.web;

import com.dpom.agent.core.hypothesis.Hypothesis;
import com.dpom.agent.core.hypothesis.HypothesisService;
import com.dpom.agent.core.hypothesis.HypothesisStatus;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationCoordinator;
import com.dpom.agent.core.investigation.InvestigationDecision;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.investigation.HypothesisUpdate;
import com.dpom.agent.core.investigation.ToolAction;
import com.dpom.agent.core.investigation.ToolExecutionResult;
import com.dpom.agent.core.observation.Observation;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.HypothesisDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.ObservationDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 调查协调器集成测试：正常完成、预算截断、等待人工、重启恢复、否定证据保留。
 */
@SpringBootTest
class InvestigationCoordinatorTest {

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    @Autowired
    private HypothesisDao hypothesisDao;

    @Autowired
    private ObservationDao observationDao;

    @Autowired
    private ConclusionDao conclusionDao;

    @Autowired
    private HypothesisService hypothesisService;

    @Autowired
    private InvestigationCoordinator coordinator;

    @Test
    void completesNormally() {
        long investigationId = createInvestigation(100, 100, 1800, 5);

        coordinator.run(investigationId,
                context -> context.hypotheses().isEmpty()
                        ? new InvestigationDecision.Act(new ToolAction("read_source", "{}", "读取源码"))
                        : new InvestigationDecision.Conclude(
                                InvestigationCoordinator.RESULT_ROOT_CAUSE, "事务回滚", "根因是事务回滚", "1"),
                action -> new ToolExecutionResult("codegraph", "AssetRepository.java", "insert 被调用",
                        null, null, List.of("INSERT 后事务回滚"), List.of()));

        assertThat(investigationDao.findById(investigationId).orElseThrow().status())
                .isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(hypothesisDao.findByInvestigationId(investigationId)).hasSize(1);
        assertThat(conclusionDao.findByInvestigationId(investigationId)).isPresent();
    }

    @Test
    void truncatesOnBudgetExceeded() {
        long investigationId = createInvestigation(2, 100, 1800, 5);

        coordinator.run(investigationId,
                context -> new InvestigationDecision.Act(new ToolAction("search", "{}", "搜索日志")),
                action -> new ToolExecutionResult("runtime", "无进展"));

        assertThat(investigationDao.findById(investigationId).orElseThrow().status())
                .isEqualTo(InvestigationStatus.INCONCLUSIVE);
        assertThat(conclusionDao.findByInvestigationId(investigationId).orElseThrow().resultType())
                .isEqualTo(InvestigationCoordinator.RESULT_INSUFFICIENT_EVIDENCE);
    }

    @Test
    void waitsForHuman() {
        long investigationId = createInvestigation(100, 100, 1800, 5);

        coordinator.run(investigationId,
                context -> new InvestigationDecision.WaitForHuman("需要 SRE 执行诊断脚本"),
                action -> new ToolExecutionResult("runtime", "无进展"));

        assertThat(investigationDao.findById(investigationId).orElseThrow().status())
                .isEqualTo(InvestigationStatus.WAITING_FOR_HUMAN);
    }

    @Test
    void resumesAfterWait() {
        long investigationId = createInvestigation(100, 100, 1800, 5);

        coordinator.run(investigationId,
                context -> new InvestigationDecision.WaitForHuman("需要人工"),
                action -> new ToolExecutionResult("runtime", "无进展"));
        assertThat(investigationDao.findById(investigationId).orElseThrow().status())
                .isEqualTo(InvestigationStatus.WAITING_FOR_HUMAN);

        coordinator.run(investigationId,
                context -> context.hypotheses().isEmpty()
                        ? new InvestigationDecision.Act(new ToolAction("read_source", "{}", "读源码"))
                        : new InvestigationDecision.Conclude(
                                InvestigationCoordinator.RESULT_ROOT_CAUSE, "根因", "摘要", "1"),
                action -> new ToolExecutionResult("codegraph", "A.java", "证据",
                        null, null, List.of("H1"), List.of()));

        assertThat(investigationDao.findById(investigationId).orElseThrow().status())
                .isEqualTo(InvestigationStatus.COMPLETED);
    }

    @Test
    void preservesNegativeEvidence() {
        long investigationId = createInvestigation(100, 100, 1800, 5);
        AtomicLong hypothesisId = new AtomicLong(
                hypothesisService.create(investigationId, "INSERT 后事务回滚"));

        coordinator.run(investigationId,
                context -> {
                    Hypothesis hypothesis = context.hypotheses().get(0);
                    if (hypothesis.status() == HypothesisStatus.PROPOSED) {
                        return new InvestigationDecision.Act(new ToolAction("check_logs", "{}", "查日志"));
                    }
                    return new InvestigationDecision.Conclude(
                            InvestigationCoordinator.RESULT_ROOT_CAUSE, "无回滚", "commit 成功", null);
                },
                action -> new ToolExecutionResult("runtime", null, "日志显示 commit 成功",
                        null, String.valueOf(hypothesisId.get()), List.of(),
                        List.of(new HypothesisUpdate(hypothesisId.get(), HypothesisStatus.INVALIDATED))));

        assertThat(hypothesisDao.findById(hypothesisId.get()).orElseThrow().status())
                .isEqualTo(HypothesisStatus.INVALIDATED);

        List<Observation> observations = observationDao.findByInvestigationId(investigationId);
        assertThat(observations).anyMatch(observation ->
                String.valueOf(hypothesisId.get()).equals(observation.contradictsHypothesisIds()));
    }

    private long createInvestigation(int maxSteps, int maxToolCalls, int maxDuration, int maxNoProgress) {
        long incidentId = incidentDao.insert(new Incident(
                null, "asset-service", "prod", "1.0.0", "abc123", "症状", null));
        return investigationDao.insert(new Investigation(
                null, incidentId, InvestigationStatus.CREATED, null,
                maxSteps, maxToolCalls, maxDuration, maxNoProgress, null, null));
    }
}
