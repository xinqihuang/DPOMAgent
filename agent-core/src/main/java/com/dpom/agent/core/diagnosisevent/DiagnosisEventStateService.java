package com.dpom.agent.core.diagnosisevent;

import com.dpom.agent.core.persistence.DiagnosisEventAuditDao;
import com.dpom.agent.core.persistence.DiagnosisEventOutboxDao;
import com.dpom.agent.core.persistence.command.DiagnosisEventAuditInsert;
import com.dpom.agent.core.persistence.command.DiagnosisEventLeaseCommand;
import com.dpom.agent.core.persistence.command.DiagnosisEventTransitionCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 在同一事务中提交发件箱状态与追加式审计。
 */
@Service
public class DiagnosisEventStateService {

    private final DiagnosisEventOutboxDao outboxDao;
    private final DiagnosisEventAuditDao auditDao;

    /** 创建事务状态服务。 */
    public DiagnosisEventStateService(DiagnosisEventOutboxDao outboxDao, DiagnosisEventAuditDao auditDao) {
        this.outboxDao = outboxDao;
        this.auditDao = auditDao;
    }

    /** 获取租约并同时记录租约和尝试审计。 */
    @Transactional
    public boolean acquireLease(DiagnosisEventOutbox event, DiagnosisEventLeaseCommand command) {
        if (outboxDao.acquireLease(command) != 1) {
            return false;
        }
        append(event, "LEASED", "SUCCESS", null);
        append(event, "ATTEMPTED", "STARTED", null);
        return true;
    }

    /** 恢复一批过期租约并逐条追加审计。 */
    @Transactional
    public void recoverExpired(LocalDateTime now, int limit) {
        for (Long id : outboxDao.findExpiredLeaseIds(now, limit)) {
            DiagnosisEventOutbox event = outboxDao.findById(id).orElseThrow();
            if (outboxDao.recoverExpiredById(id, now) == 1) {
                append(event, "LEASE_RECOVERED", "SUCCESS", "LEASE_EXPIRED");
            }
        }
    }

    /** 确认投递成功。 */
    @Transactional
    public void markDelivered(DiagnosisEventOutbox event, String result, LocalDateTime now) {
        requireUpdated(outboxDao.markDelivered(command(event, now, null, null)));
        append(event, "ACKNOWLEDGED", result, null);
    }

    /** 安排下一次有界重试。 */
    @Transactional
    public void scheduleRetry(DiagnosisEventOutbox event, String errorCode,
                              LocalDateTime nextAttemptAt, LocalDateTime now) {
        requireUpdated(outboxDao.scheduleRetry(command(event, now, nextAttemptAt, errorCode)));
        append(event, "RETRY_SCHEDULED", "RETRYABLE_FAILURE", errorCode);
    }

    /** 将不可继续投递的事件转为 DEAD。 */
    @Transactional
    public void markDead(DiagnosisEventOutbox event, String result, String errorCode, LocalDateTime now) {
        requireUpdated(outboxDao.markDead(command(event, now, null, errorCode)));
        append(event, "DEAD", result, errorCode);
    }

    private DiagnosisEventTransitionCommand command(DiagnosisEventOutbox event, LocalDateTime now,
                                                     LocalDateTime nextAttemptAt, String errorCode) {
        return new DiagnosisEventTransitionCommand(event.id(), event.leaseToken(), now, nextAttemptAt, errorCode);
    }

    private void append(DiagnosisEventOutbox event, String action, String result, String errorCode) {
        auditDao.append(new DiagnosisEventAuditInsert(event.eventId(), event.eventType(), action, result,
                errorCode, null, null, event.eventId()));
    }

    private void requireUpdated(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("DIAGNOSIS_EVENT_LEASE_LOST");
        }
    }
}
