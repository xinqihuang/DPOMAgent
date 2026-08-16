package com.dpom.agent.core.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.dpom.agent.core.investigation.InvestigationRun;
import com.dpom.agent.core.persistence.command.InvestigationRunInsert;

/**
 * 调查运行持久化 Mapper（MyBatis XML）。
 */
@Mapper
public interface InvestigationRunDao {

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 运行（可为空）
     */
    Optional<InvestigationRun> findById(@Param("id") long id);

    /**
     * 按调查查询运行列表。
     *
     * @param investigationId 调查 id
     * @return 运行列表
     */
    List<InvestigationRun> findByInvestigationId(@Param("investigationId") long investigationId);

    /**
     * 插入运行，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(InvestigationRunInsert command);

    /**
     * 结束运行。
     *
     * @param id      主键
     * @param endedAt 结束时间
     */
    void finish(@Param("id") long id, @Param("endedAt") LocalDateTime endedAt);
}
