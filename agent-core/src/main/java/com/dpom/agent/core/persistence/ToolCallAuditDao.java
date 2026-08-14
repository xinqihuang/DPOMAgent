package com.dpom.agent.core.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import com.dpom.agent.core.tool.ToolCallAudit;

/**
 * 工具调用审计持久化 DAO（仅追加）。
 */
@Repository
public class ToolCallAuditDao {

    /** 审计行映射器。 */
    private static final RowMapper<ToolCallAudit> MAPPER = (rs, rowNum) -> new ToolCallAudit(
            rs.getLong("id"),
            rs.getLong("investigation_id"),
            rs.getObject("run_id", Long.class),
            rs.getString("tool_name"),
            rs.getString("tool_input"),
            rs.getString("tool_output_summary"),
            rs.getObject("duration_ms", Long.class),
            rs.getObject("success", Boolean.class),
            rs.getString("error_message"),
            rs.getObject("created_at", LocalDateTime.class));

    private final JdbcClient jdbcClient;

    /**
     * 构造器注入。
     *
     * @param jdbcClient JDBC 客户端
     */
    public ToolCallAuditDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 追加一条审计记录，返回生成主键。
     *
     * @param audit 审计记录
     * @return 生成主键
     */
    public long append(ToolCallAudit audit) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO tool_call_audit (investigation_id, run_id, tool_name, tool_input,
                                              tool_output_summary, duration_ms, success, error_message)
                VALUES (:investigationId, :runId, :toolName, :toolInput,
                        :toolOutputSummary, :durationMs, :success, :errorMessage)
                """)
                .param("investigationId", audit.investigationId())
                .param("runId", audit.runId())
                .param("toolName", audit.toolName())
                .param("toolInput", audit.toolInput())
                .param("toolOutputSummary", audit.toolOutputSummary())
                .param("durationMs", audit.durationMs())
                .param("success", audit.success())
                .param("errorMessage", audit.errorMessage())
                .update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    /**
     * 按调查查询审计记录列表。
     *
     * @param investigationId 调查 id
     * @return 审计记录列表
     */
    public List<ToolCallAudit> findByInvestigationId(long investigationId) {
        return jdbcClient.sql("SELECT * FROM tool_call_audit WHERE investigation_id = :investigationId ORDER BY id")
                .param("investigationId", investigationId)
                .query(MAPPER).list();
    }
}
