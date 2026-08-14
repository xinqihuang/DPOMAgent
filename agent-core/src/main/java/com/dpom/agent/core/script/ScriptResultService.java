package com.dpom.agent.core.script;

import com.dpom.agent.core.investigation.InvestigationStateMachine;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.observation.ObservationService;
import com.dpom.agent.core.persistence.InvestigationDao;
import org.springframework.stereotype.Service;

/**
 * 脚本结果回传服务：把 SRE 回传的脚本结果转为观察，并在需要时恢复调查。
 */
@Service
public class ScriptResultService {

    private final ObservationService observationService;
    private final InvestigationDao investigationDao;
    private final InvestigationStateMachine stateMachine;

    /**
     * 构造器注入。
     *
     * @param observationService 观察服务
     * @param investigationDao   调查 DAO
     * @param stateMachine       状态机
     */
    public ScriptResultService(ObservationService observationService, InvestigationDao investigationDao,
                               InvestigationStateMachine stateMachine) {
        this.observationService = observationService;
        this.investigationDao = investigationDao;
        this.stateMachine = stateMachine;
    }

    /**
     * 回传脚本执行结果。
     *
     * @param investigationId 调查 id
     * @param scriptId        脚本 id
     * @param summary         结果摘要
     * @return 新观察 id
     */
    public long submitResult(long investigationId, long scriptId, String summary) {
        long observationId = observationService.record(investigationId, null, "script",
                "script:" + scriptId, null, null, null, summary, null);
        InvestigationStatus status = investigationDao.findById(investigationId).orElseThrow().status();
        if (status == InvestigationStatus.WAITING_FOR_HUMAN) {
            stateMachine.assertTransition(status, InvestigationStatus.RESEARCHING);
            investigationDao.updateStatus(investigationId, InvestigationStatus.RESEARCHING);
        }
        return observationId;
    }
}
