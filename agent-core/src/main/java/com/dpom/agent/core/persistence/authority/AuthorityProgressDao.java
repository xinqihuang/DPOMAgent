package com.dpom.agent.core.persistence.authority;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 权威 Diagnosis Progress Outbox Mapper。 */
@Mapper
public interface AuthorityProgressDao {

    int insertIntent(ProgressPublicationIntentRow row);

    /** 是否已用 sequence 1 明确准入该 Investigation 的 Kafka Progress 流。 */
    boolean hasAdmission(@Param("investigationId") String investigationId);

    Optional<ProgressPublicationIntentRow> findIntent(@Param("progressId") String progressId);

    List<String> findReadyIntentIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int acquireIntentLease(@Param("progressId") String progressId, @Param("owner") String owner,
                           @Param("token") String token, @Param("expiresAt") LocalDateTime expiresAt,
                           @Param("now") LocalDateTime now);

    int recoverExpiredIntents(@Param("now") LocalDateTime now);

    int markIntentDelivered(@Param("progressId") String progressId, @Param("token") String token,
                            @Param("now") LocalDateTime now);

    int retryIntent(@Param("progressId") String progressId, @Param("token") String token,
                    @Param("errorCode") String errorCode, @Param("eligibleAt") LocalDateTime eligibleAt,
                    @Param("now") LocalDateTime now);

    int deadIntent(@Param("progressId") String progressId, @Param("token") String token,
                   @Param("errorCode") String errorCode, @Param("now") LocalDateTime now);

    int replayDeadIntent(@Param("progressId") String progressId, @Param("now") LocalDateTime now);

    int appendIntentAttempt(@Param("progressId") String progressId,
                            @Param("attemptNumber") int attemptNumber,
                            @Param("outcome") String outcome, @Param("errorCode") String errorCode,
                            @Param("createdAt") LocalDateTime createdAt);

    int countIntentsByStatus(@Param("status") String status);
}
