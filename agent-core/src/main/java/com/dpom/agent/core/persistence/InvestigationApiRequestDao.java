package com.dpom.agent.core.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 调查 API 幂等/执行记录 DAO。
 */
@Repository
public class InvestigationApiRequestDao {

    private static final RowMapper<ApiRequestRecord> MAPPER = (rs, rowNum) -> new ApiRequestRecord(
            rs.getLong("id"), rs.getString("idempotency_key"), rs.getString("payload_hash"),
            rs.getLong("investigation_id"), rs.getString("status"),
            rs.getObject("started_at", LocalDateTime.class), rs.getObject("completed_at", LocalDateTime.class),
            rs.getString("last_error_code"), rs.getObject("created_at", LocalDateTime.class));

    private final JdbcClient jdbcClient;

    public InvestigationApiRequestDao(JdbcClient jdbcClient) { this.jdbcClient = jdbcClient; }

    public long insert(String idempotencyKey, String payloadHash, long investigationId, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO investigation_api_request (idempotency_key, payload_hash, investigation_id, status)
                VALUES (:key, :hash, :investigationId, :status)
                """).param("key", idempotencyKey).param("hash", payloadHash)
                .param("investigationId", investigationId).param("status", status).update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    public Optional<ApiRequestRecord> findByIdempotencyKey(String idempotencyKey) {
        return jdbcClient.sql("SELECT * FROM investigation_api_request WHERE idempotency_key = :key")
                .param("key", idempotencyKey).query(MAPPER).optional();
    }

    public Optional<ApiRequestRecord> findByInvestigationId(long investigationId) {
        return jdbcClient.sql("SELECT * FROM investigation_api_request WHERE investigation_id = :id ORDER BY id DESC")
                .param("id", investigationId).query(MAPPER).optional();
    }

    public void updateRunning(long id) {
        jdbcClient.sql("UPDATE investigation_api_request SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("id", id).update();
    }

    public void updateDone(long id, String status, String errorCode) {
        jdbcClient.sql("""
                UPDATE investigation_api_request SET status = :status, completed_at = CURRENT_TIMESTAMP,
                last_error_code = :errorCode WHERE id = :id
                """).param("status", status).param("errorCode", errorCode).param("id", id).update();
    }
}
