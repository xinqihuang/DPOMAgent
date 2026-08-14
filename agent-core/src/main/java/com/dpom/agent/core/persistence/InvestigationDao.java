package com.dpom.agent.core.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationStatus;

/**
 * 调查持久化 DAO。
 */
@Repository
public class InvestigationDao {

    /** 调查行映射器。 */
    private static final RowMapper<Investigation> MAPPER = (rs, rowNum) -> new Investigation(
            rs.getLong("id"),
            rs.getLong("incident_id"),
            InvestigationStatus.valueOf(rs.getString("status")),
            rs.getObject("current_run_id", Long.class),
            rs.getInt("max_steps"),
            rs.getInt("max_tool_calls"),
            rs.getInt("max_duration_seconds"),
            rs.getInt("max_no_progress_rounds"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class));

    private final JdbcClient jdbcClient;

    /**
     * 构造器注入。
     *
     * @param jdbcClient JDBC 客户端
     */
    public InvestigationDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 新增调查，返回生成主键。
     *
     * @param investigation 调查
     * @return 生成主键
     */
    public long insert(Investigation investigation) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO investigation (incident_id, status, current_run_id, max_steps, max_tool_calls,
                                           max_duration_seconds, max_no_progress_rounds)
                VALUES (:incidentId, :status, :currentRunId, :maxSteps, :maxToolCalls,
                        :maxDurationSeconds, :maxNoProgressRounds)
                """)
                .param("incidentId", investigation.incidentId())
                .param("status", investigation.status().name())
                .param("currentRunId", investigation.currentRunId())
                .param("maxSteps", investigation.maxSteps())
                .param("maxToolCalls", investigation.maxToolCalls())
                .param("maxDurationSeconds", investigation.maxDurationSeconds())
                .param("maxNoProgressRounds", investigation.maxNoProgressRounds())
                .update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 调查（可为空）
     */
    public Optional<Investigation> findById(long id) {
        return jdbcClient.sql("SELECT * FROM investigation WHERE id = :id")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    /**
     * 按事件查询调查列表。
     *
     * @param incidentId 事件 id
     * @return 调查列表
     */
    public List<Investigation> findByIncidentId(long incidentId) {
        return jdbcClient.sql("SELECT * FROM investigation WHERE incident_id = :incidentId ORDER BY id")
                .param("incidentId", incidentId)
                .query(MAPPER).list();
    }

    /**
     * 更新调查状态。
     *
     * @param id     主键
     * @param status 新状态
     */
    public void updateStatus(long id, InvestigationStatus status) {
        jdbcClient.sql("UPDATE investigation SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("status", status.name())
                .param("id", id)
                .update();
    }

    /**
     * 更新当前 Run。
     *
     * @param id           主键
     * @param currentRunId 当前 Run id（可为空）
     */
    public void updateCurrentRun(long id, Long currentRunId) {
        jdbcClient.sql("UPDATE investigation SET current_run_id = :currentRunId, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("currentRunId", currentRunId)
                .param("id", id)
                .update();
    }
}
