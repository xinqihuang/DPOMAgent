package com.dpom.agent.core.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.persistence.command.ConclusionInsert;

/**
 * 结论持久化 Mapper（MyBatis XML）。
 */
@Mapper
public interface ConclusionDao {

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 结论（可为空）
     */
    Optional<Conclusion> findById(@Param("id") long id);

    /**
     * 按调查查询结论。
     *
     * @param investigationId 调查 id
     * @return 结论（可为空）
     */
    Optional<Conclusion> findByInvestigationId(@Param("investigationId") long investigationId);

    /**
     * 插入结论，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(ConclusionInsert command);
}
