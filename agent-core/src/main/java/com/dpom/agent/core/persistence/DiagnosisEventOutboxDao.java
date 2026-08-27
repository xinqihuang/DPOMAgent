package com.dpom.agent.core.persistence;

import com.dpom.agent.core.diagnosisevent.DiagnosisEventOutbox;
import com.dpom.agent.core.persistence.command.DiagnosisEventLeaseCommand;
import com.dpom.agent.core.persistence.command.DiagnosisEventOutboxInsert;
import com.dpom.agent.core.persistence.command.DiagnosisEventReplayCommand;
import com.dpom.agent.core.persistence.command.DiagnosisEventTransitionCommand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Diagnosis Event 发件箱 MyBatis XML Mapper。
 */
@Mapper
public interface DiagnosisEventOutboxDao {
    Optional<DiagnosisEventOutbox> findById(@Param("id") long id);
    Optional<DiagnosisEventOutbox> findByEventId(@Param("eventId") String eventId);
    Optional<DiagnosisEventOutbox> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
    List<DiagnosisEventOutbox> findByInvestigationId(@Param("investigationId") long investigationId);
    List<Long> findReadyIds(@Param("now") LocalDateTime now, @Param("limit") int limit);
    List<Long> findExpiredLeaseIds(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int insert(DiagnosisEventOutboxInsert command);
    int acquireLease(DiagnosisEventLeaseCommand command);
    int recoverExpired(@Param("now") LocalDateTime now);
    int recoverExpiredById(@Param("id") long id, @Param("now") LocalDateTime now);
    int markDelivered(DiagnosisEventTransitionCommand command);
    int scheduleRetry(DiagnosisEventTransitionCommand command);
    int markDead(DiagnosisEventTransitionCommand command);
    int replay(DiagnosisEventReplayCommand command);
}
