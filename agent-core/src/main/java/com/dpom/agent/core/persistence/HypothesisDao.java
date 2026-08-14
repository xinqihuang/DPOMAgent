package com.dpom.agent.core.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.dpom.agent.core.hypothesis.Hypothesis;
import com.dpom.agent.core.hypothesis.HypothesisStatus;

/**
 * 假设持久化 DAO。
 */
@Repository
public class HypothesisDao {

    /** 假设行映射器。 */
    private static final RowMapper<Hypothesis> MAPPER = (rs, rowNum) -> new Hypothesis(
            rs.getLong("id"),
            rs.getLong("investigation_id"),
            rs.getObject("parent_id", Long.class),
            rs.getString("description"),
            HypothesisStatus.valueOf(rs.getString("status")),
            rs.getString("missing_checks"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class));

    private final JdbcClient jdbcClient;

    /**
     * 构造器注入。
     *
     * @param jdbcClient JDBC 客户端
     */
    public HypothesisDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 新增假设，返回生成主键。
     *
     * @param hypothesis 假设
     * @return 生成主键
     */
    public long insert(Hypothesis hypothesis) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO hypothesis (investigation_id, parent_id, description, status, missing_checks)
                VALUES (:investigationId, :parentId, :description, :status, :missingChecks)
                """)
                .param("investigationId", hypothesis.investigationId())
                .param("parentId", hypothesis.parentId())
                .param("description", hypothesis.description())
                .param("status", hypothesis.status().name())
                .param("missingChecks", hypothesis.missingChecks())
                .update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 假设（可为空）
     */
    public Optional<Hypothesis> findById(long id) {
        return jdbcClient.sql("SELECT * FROM hypothesis WHERE id = :id")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    /**
     * 按调查查询假设列表。
     *
     * @param investigationId 调查 id
     * @return 假设列表
     */
    public List<Hypothesis> findByInvestigationId(long investigationId) {
        return jdbcClient.sql("SELECT * FROM hypothesis WHERE investigation_id = :investigationId ORDER BY id")
                .param("investigationId", investigationId)
                .query(MAPPER).list();
    }

    /**
     * 更新假设状态。
     *
     * @param id     主键
     * @param status 新状态
     */
    public void updateStatus(long id, HypothesisStatus status) {
        jdbcClient.sql("UPDATE hypothesis SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("status", status.name())
                .param("id", id)
                .update();
    }
}
