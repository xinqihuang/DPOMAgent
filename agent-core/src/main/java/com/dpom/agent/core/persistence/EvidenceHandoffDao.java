package com.dpom.agent.core.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.dpom.agent.core.handoff.HandoffImport;
import com.dpom.agent.core.handoff.HandoffUpload;
import com.dpom.agent.core.persistence.command.EscalationDecisionInsert;
import com.dpom.agent.core.persistence.command.HandoffImportInsert;
import com.dpom.agent.core.persistence.command.HandoffUploadInsert;

/**
 * 证据交接持久化 Mapper（MyBatis XML）：升级判定、上传批准、研发侧导入与追加式审计。
 */
@Mapper
public interface EvidenceHandoffDao {

    /**
     * 插入升级判定，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insertEscalationDecision(EscalationDecisionInsert command);

    /**
     * 查询升级判定原始行。
     *
     * @param investigationId 调查 id
     * @return 原始行（可为空）
     */
    Optional<EscalationRow> findEscalationRow(@Param("investigationId") long investigationId);

    /**
     * 创建上传记录（初始 NOT_APPROVED），自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insertUpload(HandoffUploadInsert command);

    /**
     * 按包标识查询上传记录。
     *
     * @param packageId 包标识
     * @return 上传记录（可为空）
     */
    Optional<HandoffUpload> findUploadByPackageId(@Param("packageId") String packageId);

    /**
     * 按调查查询上传记录。
     *
     * @param investigationId 调查 id
     * @return 上传记录列表
     */
    List<HandoffUpload> findUploadByInvestigationId(@Param("investigationId") long investigationId);

    /**
     * 批准上传。
     *
     * @param id          记录主键
     * @param approverRef 外部审批引用
     * @param reason      审批理由
     * @param expiresAt   审批过期时间
     * @return 受影响行数
     */
    int approveUpload(@Param("id") long id, @Param("approverRef") String approverRef,
                      @Param("reason") String reason, @Param("expiresAt") LocalDateTime expiresAt);

    /**
     * 拒绝上传。
     *
     * @param id          记录主键
     * @param approverRef 外部审批引用
     * @param reason      拒绝理由
     * @return 受影响行数
     */
    int rejectUpload(@Param("id") long id, @Param("approverRef") String approverRef, @Param("reason") String reason);

    /**
     * 标记已上传并记录对象名。
     *
     * @param id        记录主键
     * @param objectKey 对象名
     * @return 受影响行数
     */
    int markUploaded(@Param("id") long id, @Param("objectKey") String objectKey);

    /**
     * 追加写审计事件。
     *
     * @param eventType       事件类型
     * @param result          结果
     * @param errorCode       错误码
     * @param investigationId 调查 id
     * @param packageId       包标识
     * @param correlationId   关联标识
     */
    void recordAudit(@Param("eventType") String eventType, @Param("result") String result,
                     @Param("errorCode") String errorCode, @Param("investigationId") Long investigationId,
                     @Param("packageId") String packageId, @Param("correlationId") String correlationId);

    /**
     * 查询是否已导入过该包。
     *
     * @param packageId 包标识
     * @return 导入记录（可为空）
     */
    Optional<HandoffImport> findImportByPackageId(@Param("packageId") String packageId);

    /**
     * 记录一次导入，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insertImport(HandoffImportInsert command);
}
