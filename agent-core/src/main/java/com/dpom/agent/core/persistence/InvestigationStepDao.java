package com.dpom.agent.core.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import com.dpom.agent.core.investigation.InvestigationStep;

/**
 * 调查步骤持久化 DAO（仅追加）。
 */
@Repository
public class InvestigationStepDao {

    /** 步骤行映射器。 */
    private static final RowMapper<InvestigationStep> MAPPER = (rs, rowNum) -> new InvestigationStep(
            rs.getLong("id"),
            rs.getLong("investigation_id"),
            rs.getObject("run_id", Long.class),
            rs.getInt("step_order"),
            rs.getString("step_type"),
            rs.getString("summary"),
            rs.getString("payload_json"),
            rs.getObject("created_at", LocalDateTime.class));

    private final JdbcClient jdbcClient;

    /**
     * 构造器注入。
     *
     * @param jdbcClient JDBC 客户端
     */
    public InvestigationStepDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 追加一步，返回生成主键。
     *
     * @param step 步骤
     * @return 生成主键
     */
    public long append(InvestigationStep step) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO investigation_step (investigation_id, run_id, step_order, step_type, summary, payload_json)
                VALUES (:investigationId, :runId, :stepOrder, :stepType, :summary, :payloadJson)
                """)
                .param("investigationId", step.investigationId())
                .param("runId", step.runId())
                .param("stepOrder", step.stepOrder())
                .param("stepType", step.stepType())
                .param("summary", step.summary())
                .param("payloadJson", step.payloadJson())
                .update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    /**
     * 查询某调查当前最大步骤序号（无步骤时为 0）。
     *
     * @param investigationId 调查 id
     * @return 最大步骤序号
     */
    public int maxStepOrder(long investigationId) {
        Integer max = jdbcClient.sql(
                        "SELECT COALESCE(MAX(step_order), 0) FROM investigation_step WHERE investigation_id = :investigationId")
                .param("investigationId", investigationId)
                .query(Integer.class)
                .single();
        return max == null ? 0 : max;
    }

    /**
     * 按调查查询步骤（按序号升序）。
     *
     * @param investigationId 调查 id
     * @return 步骤列表
     */
    public List<InvestigationStep> findByInvestigationId(long investigationId) {
        return jdbcClient.sql("SELECT * FROM investigation_step WHERE investigation_id = :investigationId ORDER BY step_order")
                .param("investigationId", investigationId)
                .query(MAPPER).list();
    }
}
