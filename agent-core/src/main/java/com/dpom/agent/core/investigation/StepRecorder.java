package com.dpom.agent.core.investigation;

import com.dpom.agent.core.persistence.InvestigationStepDao;
import org.springframework.stereotype.Service;

/**
 * 步骤记录器：以仅追加方式记录调查步骤。
 */
@Service
public class StepRecorder {

    private final InvestigationStepDao stepDao;

    /**
     * 构造器注入。
     *
     * @param stepDao 步骤 DAO
     */
    public StepRecorder(InvestigationStepDao stepDao) {
        this.stepDao = stepDao;
    }

    /**
     * 追加一步。
     *
     * @param investigationId 调查 id
     * @param runId           运行 id（可为空）
     * @param stepType        步骤类型
     * @param summary         摘要
     * @return 步骤 id
     */
    public long record(long investigationId, Long runId, String stepType, String summary) {
        int order = stepDao.maxStepOrder(investigationId) + 1;
        return stepDao.append(new InvestigationStep(
                null, investigationId, runId, order, stepType, summary, null, null));
    }
}
