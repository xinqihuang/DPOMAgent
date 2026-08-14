package com.dpom.agent.core.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.dpom.agent.core.observation.Observation;

/**
 * 观察（证据）持久化 DAO。
 */
@Repository
public class ObservationDao {

    /** 观察行映射器。 */
    private static final RowMapper<Observation> MAPPER = (rs, rowNum) -> new Observation(
            rs.getLong("id"),
            rs.getLong("investigation_id"),
            rs.getObject("run_id", Long.class),
            rs.getString("source"),
            rs.getString("artifact_ref"),
            rs.getString("location"),
            rs.getString("supports_hypothesis_ids"),
            rs.getString("contradicts_hypothesis_ids"),
            rs.getString("summary"),
            rs.getString("payload_json"),
            rs.getObject("created_at", LocalDateTime.class));

    private final JdbcClient jdbcClient;

    /**
     * 构造器注入。
     *
     * @param jdbcClient JDBC 客户端
     */
    public ObservationDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 新增观察，返回生成主键。
     *
     * @param observation 观察
     * @return 生成主键
     */
    public long insert(Observation observation) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO observation (investigation_id, run_id, source, artifact_ref, location,
                                          supports_hypothesis_ids, contradicts_hypothesis_ids, summary, payload_json)
                VALUES (:investigationId, :runId, :source, :artifactRef, :location,
                        :supportsHypothesisIds, :contradictsHypothesisIds, :summary, :payloadJson)
                """)
                .param("investigationId", observation.investigationId())
                .param("runId", observation.runId())
                .param("source", observation.source())
                .param("artifactRef", observation.artifactRef())
                .param("location", observation.location())
                .param("supportsHypothesisIds", observation.supportsHypothesisIds())
                .param("contradictsHypothesisIds", observation.contradictsHypothesisIds())
                .param("summary", observation.summary())
                .param("payloadJson", observation.payloadJson())
                .update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 观察（可为空）
     */
    public Optional<Observation> findById(long id) {
        return jdbcClient.sql("SELECT * FROM observation WHERE id = :id")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    /**
     * 按调查查询观察列表。
     *
     * @param investigationId 调查 id
     * @return 观察列表
     */
    public List<Observation> findByInvestigationId(long investigationId) {
        return jdbcClient.sql("SELECT * FROM observation WHERE investigation_id = :investigationId ORDER BY id")
                .param("investigationId", investigationId)
                .query(MAPPER).list();
    }
}
