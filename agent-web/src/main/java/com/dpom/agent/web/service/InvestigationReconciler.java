package com.dpom.agent.web.service;

import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.InvestigationApiRequestDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.command.ConclusionInsert;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动 reconciliation：把重启遗留的非终态调查标记 FAILED，避免永久运行态（不自动重放生产动作）。
 * 结论恰一次、api_request 同步 FAILED、重复调用幂等；`dpom.reconciliation.recovered` 计实际恢复数。
 */
@Component
public class InvestigationReconciler {

    private static final String RECOVERED = "dpom.reconciliation.recovered";

    private final InvestigationDao investigationDao;
    private final ConclusionDao conclusionDao;
    private final InvestigationApiRequestDao apiRequestDao;
    private final MeterRegistry meterRegistry;

    public InvestigationReconciler(InvestigationDao investigationDao, ConclusionDao conclusionDao,
            InvestigationApiRequestDao apiRequestDao, MeterRegistry meterRegistry) {
        this.investigationDao = investigationDao;
        this.conclusionDao = conclusionDao;
        this.apiRequestDao = apiRequestDao;
        this.meterRegistry = meterRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        for (Investigation inv : investigationDao.findNonTerminal()) {
            int affected = investigationDao.updateStatusIfActive(inv.id(), InvestigationStatus.FAILED);
            if (affected != 1) {
                continue;
            }
            if (conclusionDao.findByInvestigationId(inv.id()).isEmpty()) {
                ConclusionInsert conclusionCommand = new ConclusionInsert(inv.id(), "FAILED", null, null,
                        null, null, "进程重启，任务标记失败可恢复");
                conclusionDao.insert(conclusionCommand);
            }
            apiRequestDao.findByInvestigationId(inv.id()).ifPresent(record ->
                    apiRequestDao.updateDone(record.id(), "FAILED", "RECONCILED_AFTER_RESTART"));
            try {
                Counter.builder(RECOVERED).description("reconciliation 实际恢复的 investigation 数")
                        .register(meterRegistry).increment();
            } catch (RuntimeException ignored) {
                // best-effort observability，不中断 reconciliation
            }
        }
    }
}
