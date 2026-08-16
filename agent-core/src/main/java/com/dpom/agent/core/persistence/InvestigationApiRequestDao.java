package com.dpom.agent.core.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

import com.dpom.agent.core.persistence.command.ApiRequestInsert;

/**
 * 调查 API 幂等/执行记录 Mapper（MyBatis XML）。
 */
@Mapper
public interface InvestigationApiRequestDao {

    /**
     * 按幂等键查询。
     *
     * @param idempotencyKey 幂等键
     * @return 记录（可为空）
     */
    Optional<ApiRequestRecord> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 按调查查询最新记录。
     *
     * @param investigationId 调查 id
     * @return 记录（可为空）
     */
    Optional<ApiRequestRecord> findByInvestigationId(@Param("investigationId") long investigationId);

    /**
     * 插入记录，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(ApiRequestInsert command);

    /**
     * 置为运行中。
     *
     * @param id 主键
     */
    void updateRunning(@Param("id") long id);

    /**
     * 置为完成/失败。
     *
     * @param id        主键
     * @param status    状态
     * @param errorCode 错误码
     */
    void updateDone(@Param("id") long id, @Param("status") String status, @Param("errorCode") String errorCode);
}
