package com.dpom.agent.core.investigation;

import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.hypothesis.Hypothesis;
import com.dpom.agent.core.hypothesis.HypothesisService;
import com.dpom.agent.core.observation.Observation;
import com.dpom.agent.core.observation.ObservationService;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.HypothesisDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.InvestigationRunDao;
import com.dpom.agent.core.persistence.InvestigationStepDao;
import com.dpom.agent.core.persistence.ObservationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 调查协调器：驱动可恢复、有预算上限的自动调查循环。
 *
 * <p>每轮执行一个受限工具动作，检查预算，更新假设，最终综合结论。</p>
 */
@Service
public class InvestigationCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(InvestigationCoordinator.class);

    /** 结论类型：找到根因。 */
    public static final String RESULT_ROOT_CAUSE = "ROOT_CAUSE_FOUND";

    /** 结论类型：证据不足。 */
    public static final String RESULT_INSUFFICIENT_EVIDENCE = "INSUFFICIENT_EVIDENCE";

    private final InvestigationDao investigationDao;
    private final InvestigationRunDao runDao;
    private final InvestigationStepDao stepDao;
    private final ObservationDao observationDao;
    private final HypothesisDao hypothesisDao;
    private final ConclusionDao conclusionDao;
    private final StepRecorder stepRecorder;
    private final HypothesisService hypothesisService;
    private final ObservationService observationService;
    private final InvestigationStateMachine stateMachine;

    /**
     * 构造器注入。
     *
     * @param investigationDao    调查 DAO
     * @param runDao              运行 DAO
     * @param stepDao             步骤 DAO
     * @param observationDao      观察 DAO
     * @param hypothesisDao       假设 DAO
     * @param conclusionDao       结论 DAO
     * @param stepRecorder        步骤记录器
     * @param hypothesisService   假设服务
     * @param observationService  观察服务
     * @param stateMachine        状态机
     */
    public InvestigationCoordinator(InvestigationDao investigationDao, InvestigationRunDao runDao,
                                    InvestigationStepDao stepDao, ObservationDao observationDao,
                                    HypothesisDao hypothesisDao, ConclusionDao conclusionDao,
                                    StepRecorder stepRecorder, HypothesisService hypothesisService,
                                    ObservationService observationService, InvestigationStateMachine stateMachine) {
        this.investigationDao = investigationDao;
        this.runDao = runDao;
        this.stepDao = stepDao;
        this.observationDao = observationDao;
        this.hypothesisDao = hypothesisDao;
        this.conclusionDao = conclusionDao;
        this.stepRecorder = stepRecorder;
        this.hypothesisService = hypothesisService;
        this.observationService = observationService;
        this.stateMachine = stateMachine;
    }

    /**
     * 运行调查（可对 CREATED 或 WAITING_FOR_HUMAN 状态启动/恢复）。
     *
     * @param investigationId 调查 id
     * @param brain           决策大脑
     * @param executor        工具执行器
     */
    public void run(long investigationId, Brain brain, ToolExecutor executor) {
        Investigation investigation = load(investigationId);
        InvestigationStatus status = investigation.status();
        if (isTerminal(status)) {
            throw new IllegalStateException("调查已终结：" + status);
        }

        long runId = runDao.insert(new InvestigationRun(
                null, investigationId, null, null, null, null, null));
        investigationDao.updateCurrentRun(investigationId, runId);

        if (status == InvestigationStatus.CREATED) {
            transition(investigationId, InvestigationStatus.CREATED, InvestigationStatus.SCOPING);
        } else if (status == InvestigationStatus.WAITING_FOR_HUMAN) {
            transition(investigationId, InvestigationStatus.WAITING_FOR_HUMAN, InvestigationStatus.RESEARCHING);
        }
        stepRecorder.record(investigationId, runId, "START", "开始调查 run=" + runId);

        LocalDateTime startedAt = LocalDateTime.now();
        InvestigationBudget budget = InvestigationBudget.from(load(investigationId), startedAt);
        executeLoop(investigationId, runId, brain, executor, budget);
    }

    /**
     * 执行决策循环，直到终结、等待人工或预算耗尽。
     */
    private void executeLoop(long investigationId, long runId, Brain brain, ToolExecutor executor,
                             InvestigationBudget budget) {
        while (true) {
            Investigation investigation = load(investigationId);
            InvestigationStatus status = investigation.status();

            if (budget.isExceeded(LocalDateTime.now())) {
                finalize(investigationId, runId, RESULT_INSUFFICIENT_EVIDENCE, null, "预算耗尽", null);
                return;
            }
            if (status == InvestigationStatus.FORMING_HYPOTHESES) {
                transition(investigationId, status, InvestigationStatus.VALIDATING);
                status = InvestigationStatus.VALIDATING;
            }

            InvestigationDecision decision = brain.decide(buildContext(investigationId));
            if (decision instanceof InvestigationDecision.Act act) {
                handleAct(investigationId, runId, status, act, executor, budget);
            } else if (decision instanceof InvestigationDecision.WaitForHuman wait) {
                stepRecorder.record(investigationId, runId, "WAIT", wait.reason());
                transition(investigationId, status, InvestigationStatus.WAITING_FOR_HUMAN);
                LOG.info("调查 {} 等待人工：{}", investigationId, wait.reason());
                return;
            } else if (decision instanceof InvestigationDecision.UpdateHypotheses update) {
                handleUpdate(investigationId, runId, update, budget);
            } else if (decision instanceof InvestigationDecision.Conclude conclude) {
                finalize(investigationId, runId, conclude.resultType(), conclude.rootCause(),
                        conclude.summary(), conclude.evidenceIds());
                return;
            }
        }
    }

    /**
     * 处理一个工具动作决策。
     */
    private void handleAct(long investigationId, long runId, InvestigationStatus status,
                           InvestigationDecision.Act act, ToolExecutor executor, InvestigationBudget budget) {
        InvestigationStatus next = status;
        if (status == InvestigationStatus.SCOPING) {
            transition(investigationId, status, InvestigationStatus.RESEARCHING);
            next = InvestigationStatus.RESEARCHING;
        }

        budget.recordToolCall();
        ToolExecutionResult result = executor.execute(act.action());

        stepRecorder.record(investigationId, runId, "TOOL", act.action().name() + "：" + act.action().summary());
        observationService.record(investigationId, runId, result.source(), null, result.location(),
                result.supportsHypothesisIds(), result.contradictsHypothesisIds(), result.summary(), null);

        for (String description : result.newHypotheses()) {
            hypothesisService.create(investigationId, description);
        }
        for (HypothesisUpdate update : result.hypothesisUpdates()) {
            hypothesisService.updateStatus(update.hypothesisId(), update.newStatus());
        }
        budget.recordStep();

        boolean progressed = !result.newHypotheses().isEmpty() || !result.hypothesisUpdates().isEmpty();
        if (progressed) {
            budget.recordProgress();
        } else {
            budget.recordNoProgress();
        }

        if (next == InvestigationStatus.RESEARCHING && !result.newHypotheses().isEmpty()) {
            transition(investigationId, InvestigationStatus.RESEARCHING, InvestigationStatus.FORMING_HYPOTHESES);
        }
    }

    /**
     * 应用假设新建/更新（不调用工具）。
     */
    private void handleUpdate(long investigationId, long runId,
                              InvestigationDecision.UpdateHypotheses update, InvestigationBudget budget) {
        for (String description : update.newHypotheses()) {
            hypothesisService.create(investigationId, description);
        }
        for (HypothesisUpdate hypothesisUpdate : update.hypothesisUpdates()) {
            hypothesisService.updateStatus(hypothesisUpdate.hypothesisId(), hypothesisUpdate.newStatus());
        }
        stepRecorder.record(investigationId, runId, "HYPOTHESIS", "解释证据并更新假设");
        budget.recordStep();
        if (!update.newHypotheses().isEmpty() || !update.hypothesisUpdates().isEmpty()) {
            budget.recordProgress();
        } else {
            budget.recordNoProgress();
        }
        InvestigationStatus status = load(investigationId).status();
        if (status == InvestigationStatus.RESEARCHING && !update.newHypotheses().isEmpty()) {
            transition(investigationId, InvestigationStatus.RESEARCHING, InvestigationStatus.FORMING_HYPOTHESES);
        }
    }

    /**
     * 综合结论并转移到终态。
     */
    private void finalize(long investigationId, long runId, String resultType, String rootCause,
                          String summary, String evidenceIds) {
        InvestigationStatus status = load(investigationId).status();
        InvestigationStatus terminal = RESULT_ROOT_CAUSE.equals(resultType)
                ? InvestigationStatus.COMPLETED : InvestigationStatus.INCONCLUSIVE;

        if (stateMachine.canTransition(status, InvestigationStatus.SYNTHESIZING)) {
            transition(investigationId, status, InvestigationStatus.SYNTHESIZING);
            transition(investigationId, InvestigationStatus.SYNTHESIZING, terminal);
        } else if (stateMachine.canTransition(status, terminal)) {
            transition(investigationId, status, terminal);
        } else {
            transition(investigationId, status, InvestigationStatus.FAILED);
            terminal = InvestigationStatus.FAILED;
        }

        conclusionDao.insert(new Conclusion(null, investigationId, resultType, rootCause, evidenceIds, null,
                summary, null));
        runDao.finish(runId, LocalDateTime.now());
        LOG.info("调查 {} 终结：{}", investigationId, terminal);
    }

    /**
     * 加载调查。
     */
    private Investigation load(long investigationId) {
        return investigationDao.findById(investigationId)
                .orElseThrow(() -> new IllegalArgumentException("调查不存在：" + investigationId));
    }

    /**
     * 构建当前上下文。
     */
    private InvestigationContext buildContext(long investigationId) {
        List<InvestigationStep> steps = stepDao.findByInvestigationId(investigationId);
        List<Observation> observations = observationDao.findByInvestigationId(investigationId);
        List<Hypothesis> hypotheses = hypothesisDao.findByInvestigationId(investigationId);
        return new InvestigationContext(load(investigationId), steps, observations, hypotheses);
    }

    /**
     * 执行一次状态迁移。
     */
    private void transition(long investigationId, InvestigationStatus from, InvestigationStatus to) {
        stateMachine.assertTransition(from, to);
        investigationDao.updateStatus(investigationId, to);
    }

    /**
     * 判断是否终态。
     */
    private static boolean isTerminal(InvestigationStatus status) {
        return status == InvestigationStatus.COMPLETED || status == InvestigationStatus.INCONCLUSIVE
                || status == InvestigationStatus.FAILED || status == InvestigationStatus.CANCELLED;
    }
}