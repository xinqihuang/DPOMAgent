package com.dpom.agent.core.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

import com.dpom.agent.core.conclusion.Conclusion;

/**
 * 结论持久化 DAO。
 */
@Repository
public class ConclusionDao {

    /** 结论行映射器。 */
    private static final RowMapper<Conclusion> MAPPER = (rs, rowNum) -> new Conclusion(
            rs.getLong("id"),
            rs.getLong("investigation_id"),
            rs.getString("result_type"),
            rs.getString("root_cause"),
            rs.getString("evidence_ids"),
            rs.getString("unresolved_questions"),
            rs.getString("summary"),
            rs.getObject("created_at", LocalDateTime.class));

    private final JdbcClient jdbcClient;

    /**
     * 构造器注入。
     *
     * @param jdbcClient JDBC 客户端
     */
    public ConclusionDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 新增结论，返回生成主键。
     *
     * @param conclusion 结论
     * @return 生成主键
     */
    public long insert(Conclusion conclusion) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO conclusion (investigation_id, result_type, root_cause, evidence_ids,
                                         unresolved_questions, summary)
                VALUES (:investigationId, :resultType, :rootCause, :evidenceIds, :unresolvedQuestions, :summary)
                """)
                .param("investigationId", conclusion.investigationId())
                .param("resultType", conclusion.resultType())
                .param("rootCause", conclusion.rootCause())
                .param("evidenceIds", conclusion.evidenceIds())
                .param("unresolvedQuestions", conclusion.unresolvedQuestions())
                .param("summary", conclusion.summary())
                .update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 结论（可为空）
     */
    public Optional<Conclusion> findById(long id) {
        return jdbcClient.sql("SELECT * FROM conclusion WHERE id = :id")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    /**
     * 按调查查询结论。
     *
     * @param investigationId 调查 id
     * @return 结论（可为空）
     */
    public Optional<Conclusion> findByInvestigationId(long investigationId) {
        return jdbcClient.sql("SELECT * FROM conclusion WHERE investigation_id = :investigationId ORDER BY id DESC")
                .param("investigationId", investigationId)
                .query(MAPPER)
                .optional();
    }
}
