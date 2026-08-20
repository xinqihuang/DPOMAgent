package com.dpom.agent.core.alarm;

import com.dpom.agent.common.alarm.AlarmIncidentTriggerPort;
import com.dpom.agent.common.alarm.AlarmIncidentTriggerRequest;
import com.dpom.agent.common.alarm.AlarmIncidentTriggerResult;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.command.IncidentInsert;
import com.dpom.agent.core.persistence.command.InvestigationInsert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 告警事件触发诊断端口实现：在 agent-core 内创建 Incident 与 Investigation（CREATED）并记录触发关系。
 *
 * <p>仅落库调查记录；LLM 执行派发由 agent-web 装配层负责，本实现不引入 LLM/适配器依赖。
 * 触发关系以日志记录 alarmIncidentId → investigationId，告警侧审计由 agent-alarm 在调用前后写入。</p>
 */
@Component
public class AlarmIncidentTriggerAdapter implements AlarmIncidentTriggerPort {

    private static final Logger LOG = LoggerFactory.getLogger(AlarmIncidentTriggerAdapter.class);

    private final IncidentDao incidentDao;
    private final InvestigationDao investigationDao;
    private final TransactionTemplate transactionTemplate;

    /**
     * 构造触发适配器。
     *
     * @param incidentDao       事件持久化
     * @param investigationDao  调查持久化
     * @param transactionManager 事务管理器
     */
    public AlarmIncidentTriggerAdapter(IncidentDao incidentDao, InvestigationDao investigationDao,
            PlatformTransactionManager transactionManager) {
        this.incidentDao = incidentDao;
        this.investigationDao = investigationDao;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public AlarmIncidentTriggerResult trigger(AlarmIncidentTriggerRequest request) {
        Long investigationId = transactionTemplate.execute(status -> createInvestigation(request));
        LOG.info("告警事件 {} 触发调查 investigationId={}", request.incidentId(), investigationId);
        return AlarmIncidentTriggerResult.triggered(investigationId);
    }

    private Long createInvestigation(AlarmIncidentTriggerRequest request) {
        IncidentInsert incidentCommand = new IncidentInsert(request.serviceCode(), request.environment(),
                null, null, request.summary());
        incidentDao.insert(incidentCommand);
        long incidentId = incidentCommand.getId();
        InvestigationInsert investigationCommand = new InvestigationInsert(incidentId,
                InvestigationStatus.CREATED, null, 30, 60, 1800, 5);
        investigationDao.insert(investigationCommand);
        return investigationCommand.getId();
    }
}
