package com.dpom.agent.core.persistence;

import com.dpom.agent.core.script.ScriptArtifact;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 脚本工件持久化 DAO。
 */
@Repository
public class ScriptArtifactDao {

    /** 脚本工件行映射器。 */
    private static final RowMapper<ScriptArtifact> MAPPER = (rs, rowNum) -> new ScriptArtifact(
            rs.getLong("id"),
            rs.getLong("investigation_id"),
            rs.getString("script_type"),
            rs.getString("language"),
            rs.getString("purpose"),
            rs.getString("risk_level"),
            rs.getBoolean("read_only"),
            rs.getString("approval_status"),
            rs.getString("preconditions"),
            rs.getString("verification"),
            rs.getString("rollback"),
            rs.getString("content"),
            rs.getString("hypotheses_to_validate"),
            rs.getString("expected_output"),
            rs.getString("instructions"),
            rs.getString("root_cause"),
            rs.getString("evidence_ids"),
            rs.getString("target"),
            rs.getObject("created_at", LocalDateTime.class));

    private final JdbcClient jdbcClient;

    /**
     * 构造器注入。
     *
     * @param jdbcClient JDBC 客户端
     */
    public ScriptArtifactDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 新增脚本工件，返回生成主键。
     *
     * @param artifact 脚本工件
     * @return 生成主键
     */
    public long insert(ScriptArtifact artifact) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO script_artifact (investigation_id, script_type, language, purpose, risk_level,
                                              read_only, approval_status, preconditions, verification, rollback,
                                              content, hypotheses_to_validate, expected_output, instructions,
                                              root_cause, evidence_ids, target)
                VALUES (:investigationId, :type, :language, :purpose, :riskLevel,
                        :readOnly, :approvalStatus, :preconditions, :verification, :rollback,
                        :content, :hypothesesToValidate, :expectedOutput, :instructions,
                        :rootCause, :evidenceIds, :target)
                """)
                .param("investigationId", artifact.investigationId())
                .param("type", artifact.type())
                .param("language", artifact.language())
                .param("purpose", artifact.purpose())
                .param("riskLevel", artifact.riskLevel())
                .param("readOnly", artifact.readOnly())
                .param("approvalStatus", artifact.approvalStatus())
                .param("preconditions", artifact.preconditions())
                .param("verification", artifact.verification())
                .param("rollback", artifact.rollback())
                .param("content", artifact.content())
                .param("hypothesesToValidate", artifact.hypothesesToValidate())
                .param("expectedOutput", artifact.expectedOutput())
                .param("instructions", artifact.instructions())
                .param("rootCause", artifact.rootCause())
                .param("evidenceIds", artifact.evidenceIds())
                .param("target", artifact.target())
                .update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 脚本工件（可为空）
     */
    public Optional<ScriptArtifact> findById(long id) {
        return jdbcClient.sql("SELECT * FROM script_artifact WHERE id = :id")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    /**
     * 按调查查询脚本工件列表。
     *
     * @param investigationId 调查 id
     * @return 脚本工件列表
     */
    public List<ScriptArtifact> findByInvestigationId(long investigationId) {
        return jdbcClient.sql("SELECT * FROM script_artifact WHERE investigation_id = :investigationId ORDER BY id")
                .param("investigationId", investigationId)
                .query(MAPPER)
                .list();
    }
}
