package com.dpom.agent.core.persistence.authority;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 终态诊断源与发布意图 Mapper。 */
@Mapper
public interface AuthorityTerminalDao {

    /** 插入不可变诊断源。 */
    int insertSource(DiagnosisSourceRow row);

    /** 插入同事务发布意图。 */
    int insertIntent(PublicationIntentRow row);

    /** 按 Investigation 读取唯一诊断源。 */
    Optional<DiagnosisSourceRow> findSource(@Param("investigationId") String investigationId);

    /** 按 Investigation 读取唯一冻结发布意图。 */
    Optional<PublicationIntentRow> findIntent(@Param("investigationId") String investigationId);

    /** 查询一个有界的可投递意图批次。 */
    List<String> findReadyIntentIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 按稳定意图身份读取冻结记录。 */
    Optional<PublicationIntentRow> findIntentById(@Param("intentId") String intentId);

    /** 使用 fencing token 原子获取租约。 */
    int acquireIntentLease(@Param("intentId") String intentId, @Param("owner") String owner,
            @Param("token") String token, @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now);

    /** 恢复进程退出后到期的租约。 */
    int recoverExpiredIntents(@Param("now") LocalDateTime now);

    /** 使用当前 fencing token 确认成功。 */
    int markIntentDelivered(@Param("intentId") String intentId, @Param("token") String token,
            @Param("now") LocalDateTime now);

    /** 使用当前 fencing token 安排有界重试。 */
    int retryIntent(@Param("intentId") String intentId, @Param("token") String token,
            @Param("errorCode") String errorCode, @Param("eligibleAt") LocalDateTime eligibleAt,
            @Param("now") LocalDateTime now);

    /** 使用当前 fencing token 终止投递。 */
    int deadIntent(@Param("intentId") String intentId, @Param("token") String token,
            @Param("errorCode") String errorCode, @Param("now") LocalDateTime now);

    /** 从 DEAD 状态授权重放且保持冻结正文不变。 */
    int replayDeadIntent(@Param("intentId") String intentId, @Param("now") LocalDateTime now);

    /** 追加不含正文和凭据的投递尝试历史。 */
    int appendIntentAttempt(@Param("intentId") String intentId, @Param("attemptNumber") int attemptNumber,
            @Param("transport") String transport, @Param("outcome") String outcome,
            @Param("errorCode") String errorCode, @Param("createdAt") LocalDateTime createdAt);

    /** 按固定状态统计积压，不读取正文。 */
    int countIntentsByStatus(@Param("status") String status);

    /** 统计可发布意图。 */
    int countPendingIntents(@Param("investigationId") String investigationId);
}
