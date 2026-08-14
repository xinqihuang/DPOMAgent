package com.dpom.agent.core.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

import com.dpom.agent.core.incident.Incident;

/**
 * 事件持久化 DAO。
 */
@Repository
public class IncidentDao {

    /** 事件行映射器。 */
    private static final RowMapper<Incident> MAPPER = (rs, rowNum) -> new Incident(
            rs.getLong("id"),
            rs.getString("service_code"),
            rs.getString("environment"),
            rs.getString("release_version"),
            rs.getString("commit_sha"),
            rs.getString("symptom"),
            rs.getObject("created_at", LocalDateTime.class));

    private final JdbcClient jdbcClient;

    /**
     * 构造器注入。
     *
     * @param jdbcClient JDBC 客户端
     */
    public IncidentDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 新增事件，返回生成主键。
     *
     * @param incident 事件
     * @return 生成主键
     */
    public long insert(Incident incident) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO incident (service_code, environment, release_version, commit_sha, symptom)
                VALUES (:serviceCode, :environment, :releaseVersion, :commitSha, :symptom)
                """)
                .param("serviceCode", incident.serviceCode())
                .param("environment", incident.environment())
                .param("releaseVersion", incident.releaseVersion())
                .param("commitSha", incident.commitSha())
                .param("symptom", incident.symptom())
                .update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 事件（可为空）
     */
    public Optional<Incident> findById(long id) {
        return jdbcClient.sql("SELECT * FROM incident WHERE id = :id")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    /**
     * 按服务编码与环境查询。
     *
     * @param serviceCode 服务编码
     * @param environment 环境
     * @return 事件（可为空）
     */
    public Optional<Incident> findByServiceCodeAndEnvironment(String serviceCode, String environment) {
        return jdbcClient.sql("SELECT * FROM incident WHERE service_code = :serviceCode AND environment = :environment ORDER BY id DESC")
                .param("serviceCode", serviceCode)
                .param("environment", environment)
                .query(MAPPER)
                .optional();
    }
}
