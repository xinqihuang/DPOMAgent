package com.dpom.agent.core.persistence;

import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 日志到代码证据束持久化 DAO：保存有界脱敏的摘要 JSON，支持按调查恢复以支撑审计。
 */
@Repository
public class EvidenceBundleDao {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JdbcClient jdbcClient;

    /**
     * 构造器注入。
     *
     * @param jdbcClient JDBC 客户端
     */
    public EvidenceBundleDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 保存证据束，返回生成主键。
     *
     * @param investigationId 调查 id
     * @param bundle          证据束
     * @return 生成主键
     */
    public long save(long investigationId, EvidenceBundle bundle) {
        try {
            return insert(investigationId, bundle.service(), bundle.commit(), MAPPER.writeValueAsString(bundle));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化证据束失败", e);
        }
    }

    /**
     * 新增证据束。
     */
    private long insert(long investigationId, String serviceCode, String commitSha, String bundleJson) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                INSERT INTO evidence_bundle (investigation_id, service_code, commit_sha, bundle_json)
                VALUES (:investigationId, :serviceCode, :commitSha, :bundleJson)
                """)
                .param("investigationId", investigationId)
                .param("serviceCode", serviceCode)
                .param("commitSha", commitSha)
                .param("bundleJson", bundleJson)
                .update(keyHolder);
        return GeneratedKeys.longValue(keyHolder);
    }

    /**
     * 按调查查询最新证据束。
     *
     * @param investigationId 调查 id
     * @return 证据束（可为空）
     */
    public Optional<EvidenceBundle> findByInvestigationId(long investigationId) {
        return jdbcClient.sql(
                        "SELECT bundle_json FROM evidence_bundle WHERE investigation_id = :id ORDER BY id DESC LIMIT 1")
                .param("id", investigationId)
                .query(String.class)
                .optional()
                .map(this::deserialize);
    }

    /**
     * 反序列化证据束。
     */
    private EvidenceBundle deserialize(String json) {
        try {
            return MAPPER.readValue(json, EvidenceBundle.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("解析证据束失败", e);
        }
    }
}
