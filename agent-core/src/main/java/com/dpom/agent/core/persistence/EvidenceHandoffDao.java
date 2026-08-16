package com.dpom.agent.core.persistence;

import com.dpom.agent.core.handoff.EscalationDecision;
import com.dpom.agent.core.handoff.EscalationReason;
import com.dpom.agent.core.handoff.HandoffImport;
import com.dpom.agent.core.handoff.HandoffUpload;
import com.dpom.agent.core.handoff.UploadApprovalStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 证据交接持久化 DAO：升级判定、上传批准、研发侧导入与追加式审计。
 */
@Repository
public class EvidenceHandoffDao {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final RowMapper<HandoffUpload> UPLOAD_MAPPER = (rs, rowNum) -> new HandoffUpload(
            rs.getLong("id"),
            rs.getLong("investigation_id"),
            rs.getString("package_id"),
            rs.getString("object_key"),
            rs.getInt("schema_version"),
            rs.getString("checksum"),
            rs.getLong("size_bytes"),
            UploadApprovalStatus.valueOf(rs.getString("approval_status")),
            rs.getObject("approved_at", LocalDateTime.class),
            rs.getString("approver_ref"),
            rs.getString("approval_reason"),
            rs.getObject("approval_expires_at", LocalDateTime.class),
            rs.getObject("uploaded_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class));

    private static final RowMapper<HandoffImport> IMPORT_MAPPER = (rs, rowNum) -> new HandoffImport(
            rs.getLong("id"),
            rs.getString("package_id"),
            rs.getString("service"),
            rs.getString("release"),
            rs.getString("commit"),
            rs.getObject("created_at", LocalDateTime.class));

    private final JdbcClient jdbcClient;

    /**
     * 构造器注入。
     *
     * @param jdbcClient JDBC 客户端
     */
    public EvidenceHandoffDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 追加保存升级判定。
     *
     * @param investigationId 调查 id
     * @param decision        升级判定
     * @return 生成主键
     */
    public long saveEscalationDecision(long investigationId, EscalationDecision decision) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO escalation_decision (investigation_id, eligible, reasons, missing_evidence, confidence)
                VALUES (:investigationId, :eligible, :reasons, :missingEvidence, :confidence)
                """)
                .param("investigationId", investigationId)
                .param("eligible", decision.eligible())
                .param("reasons", reasons(decision))
                .param("missingEvidence", json(decision.missingEvidence()))
                .param("confidence", decision.confidence())
                .update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    /**
     * 查询最近一次升级判定。
     *
     * @param investigationId 调查 id
     * @return 升级判定（可为空）
     */
    public Optional<EscalationDecision> findLatestEscalationDecision(long investigationId) {
        return jdbcClient.sql("""
                SELECT eligible, reasons, missing_evidence, confidence FROM escalation_decision
                WHERE investigation_id = :id ORDER BY id DESC LIMIT 1
                """)
                .param("id", investigationId)
                .query((rs, rowNum) -> new EscalationDecision(
                        rs.getBoolean("eligible"),
                        reasons(rs.getString("reasons")),
                        strings(rs.getString("missing_evidence")),
                        rs.getInt("confidence")))
                .optional();
    }

    /**
     * 创建上传记录（初始 NOT_APPROVED）。
     *
     * @param investigationId 调查 id
     * @param packageId       包标识
     * @param schemaVersion   schema 版本
     * @param checksum        校验和
     * @param sizeBytes       字节大小
     * @return 生成主键
     */
    public long createUpload(long investigationId, String packageId, int schemaVersion, String checksum,
                             long sizeBytes) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO handoff_upload (investigation_id, package_id, schema_version, checksum, size_bytes,
                                           approval_status)
                VALUES (:investigationId, :packageId, :schemaVersion, :checksum, :sizeBytes, 'NOT_APPROVED')
                """)
                .param("investigationId", investigationId)
                .param("packageId", packageId)
                .param("schemaVersion", schemaVersion)
                .param("checksum", checksum)
                .param("sizeBytes", sizeBytes)
                .update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    /**
     * 按包标识查询上传记录。
     *
     * @param packageId 包标识
     * @return 上传记录（可为空）
     */
    public Optional<HandoffUpload> findUploadByPackageId(String packageId) {
        return jdbcClient.sql("SELECT * FROM handoff_upload WHERE package_id = :packageId")
                .param("packageId", packageId)
                .query(UPLOAD_MAPPER)
                .optional();
    }

    /**
     * 按调查查询上传记录。
     *
     * @param investigationId 调查 id
     * @return 上传记录列表
     */
    public List<HandoffUpload> findUploadByInvestigationId(long investigationId) {
        return jdbcClient.sql("SELECT * FROM handoff_upload WHERE investigation_id = :id ORDER BY id DESC")
                .param("id", investigationId)
                .query(UPLOAD_MAPPER).list();
    }

    /**
     * 批准上传：写入 APPROVED + 批准时间 + 审批引用 + 理由 + 过期时间。
     *
     * @param id         记录主键
     * @param approverRef 外部审批引用
     * @param reason     审批理由
     * @param expiresAt  审批过期时间
     * @return 受影响行数
     */
    public int approveUpload(long id, String approverRef, String reason, LocalDateTime expiresAt) {
        return jdbcClient.sql("""
                UPDATE handoff_upload SET approval_status = 'APPROVED', approved_at = CURRENT_TIMESTAMP,
                    approver_ref = :approverRef, approval_reason = :reason, approval_expires_at = :expiresAt
                WHERE id = :id
                """)
                .param("approverRef", approverRef)
                .param("reason", reason)
                .param("expiresAt", expiresAt)
                .param("id", id)
                .update();
    }

    /**
     * 拒绝上传：写入 REJECTED + 审批引用 + 理由。
     *
     * @param id         记录主键
     * @param approverRef 外部审批引用
     * @param reason     拒绝理由
     * @return 受影响行数
     */
    public int rejectUpload(long id, String approverRef, String reason) {
        return jdbcClient.sql("""
                UPDATE handoff_upload SET approval_status = 'REJECTED', approver_ref = :approverRef,
                    approval_reason = :reason
                WHERE id = :id
                """)
                .param("approverRef", approverRef)
                .param("reason", reason)
                .param("id", id)
                .update();
    }

    /**
     * 标记已上传并记录对象名。
     *
     * @param id        记录主键
     * @param objectKey 对象名
     * @return 受影响行数
     */
    public int markUploaded(long id, String objectKey) {
        return jdbcClient.sql("UPDATE handoff_upload SET object_key = :objectKey, uploaded_at = CURRENT_TIMESTAMP"
                        + " WHERE id = :id")
                .param("objectKey", objectKey)
                .param("id", id)
                .update();
    }

    /**
     * 追加写审计事件（成功/失败）。
     *
     * @param eventType       事件类型（有限枚举名）
     * @param result          结果：SUCCESS / FAILURE
     * @param errorCode       稳定错误码（失败时，可为空）
     * @param investigationId 调查 id（可为空）
     * @param packageId       包标识（可为空）
     * @param correlationId   关联标识（存在时）
     */
    public void recordAudit(String eventType, String result, String errorCode, Long investigationId, String packageId,
                            String correlationId) {
        jdbcClient.sql("""
                INSERT INTO handoff_audit (event_type, result, error_code, investigation_id, package_id, correlation_id)
                VALUES (:eventType, :result, :errorCode, :investigationId, :packageId, :correlationId)
                """)
                .param("eventType", eventType)
                .param("result", result)
                .param("errorCode", errorCode)
                .param("investigationId", investigationId)
                .param("packageId", packageId)
                .param("correlationId", correlationId)
                .update();
    }

    /**
     * 查询是否已导入过该包（幂等）。
     *
     * @param packageId 包标识
     * @return 导入记录（可为空）
     */
    public Optional<HandoffImport> findImportByPackageId(String packageId) {
        return jdbcClient.sql("SELECT * FROM handoff_import WHERE package_id = :packageId")
                .param("packageId", packageId)
                .query(IMPORT_MAPPER)
                .optional();
    }

    /**
     * 记录一次导入（package_id 唯一约束作为并发幂等仲裁）。
     *
     * @param packageId 包标识
     * @param service   服务编码
     * @param release   发布版本
     * @param commit    提交 SHA
     * @return 生成主键
     */
    public long recordImport(String packageId, String service, String release, String commit) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO handoff_import (package_id, service, release, commit)
                VALUES (:packageId, :service, :release, :commit)
                """)
                .param("packageId", packageId)
                .param("service", service)
                .param("release", release)
                .param("commit", commit)
                .update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    private String reasons(EscalationDecision decision) {
        return decision.reasons() == null ? "" : String.join(",", decision.reasons().stream()
                .map(Enum::name).toList());
    }

    private List<EscalationReason> reasons(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(EscalationReason::valueOf).toList();
    }

    private String json(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化失败", e);
        }
    }

    private List<String> strings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("解析失败", e);
        }
    }
}
