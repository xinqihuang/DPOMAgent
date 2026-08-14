package com.dpom.agent.core.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.dpom.agent.core.investigation.InvestigationRun;

/**
 * 调查运行持久化 DAO。
 */
@Repository
public class InvestigationRunDao {

    /** 运行行映射器。 */
    private static final RowMapper<InvestigationRun> MAPPER = (rs, rowNum) -> new InvestigationRun(
            rs.getLong("id"),
            rs.getLong("investigation_id"),
            rs.getString("model_version"),
            rs.getString("prompt_version"),
            rs.getString("toolset_version"),
            rs.getObject("started_at", LocalDateTime.class),
            rs.getObject("ended_at", LocalDateTime.class));

    private final JdbcClient jdbcClient;

    /**
     * 构造器注入。
     *
     * @param jdbcClient JDBC 客户端
     */
    public InvestigationRunDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 新增运行，返回生成主键。
     *
     * @param run 运行
     * @return 生成主键
     */
    public long insert(InvestigationRun run) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO investigation_run (investigation_id, model_version, prompt_version, toolset_version)
                VALUES (:investigationId, :modelVersion, :promptVersion, :toolsetVersion)
                """)
                .param("investigationId", run.investigationId())
                .param("modelVersion", run.modelVersion())
                .param("promptVersion", run.promptVersion())
                .param("toolsetVersion", run.toolsetVersion())
                .update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 运行（可为空）
     */
    public Optional<InvestigationRun> findById(long id) {
        return jdbcClient.sql("SELECT * FROM investigation_run WHERE id = :id")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    /**
     * 按调查查询运行列表。
     *
     * @param investigationId 调查 id
     * @return 运行列表
     */
    public List<InvestigationRun> findByInvestigationId(long investigationId) {
        return jdbcClient.sql("SELECT * FROM investigation_run WHERE investigation_id = :investigationId ORDER BY id")
                .param("investigationId", investigationId)
                .query(MAPPER).list();
    }

    /**
     * 结束运行（写入结束时间）。
     *
     * @param id      主键
     * @param endedAt 结束时间
     */
    public void finish(long id, LocalDateTime endedAt) {
        jdbcClient.sql("UPDATE investigation_run SET ended_at = :endedAt WHERE id = :id")
                .param("endedAt", endedAt)
                .param("id", id)
                .update();
    }
}
