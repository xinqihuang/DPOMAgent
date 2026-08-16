package com.dpom.agent.core.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

import com.dpom.agent.core.hypothesis.Hypothesis;
import com.dpom.agent.core.hypothesis.HypothesisStatus;
import com.dpom.agent.core.persistence.command.HypothesisInsert;

/**
 * 假设持久化 Mapper（MyBatis XML）。
 */
@Mapper
public interface HypothesisDao {

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 假设（可为空）
     */
    Optional<Hypothesis> findById(@Param("id") long id);

    /**
     * 按调查查询假设列表。
     *
     * @param investigationId 调查 id
     * @return 假设列表
     */
    List<Hypothesis> findByInvestigationId(@Param("investigationId") long investigationId);

    /**
     * 插入假设，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(HypothesisInsert command);

    /**
     * 更新假设状态。
     *
     * @param id     主键
     * @param status 新状态
     */
    void updateStatus(@Param("id") long id, @Param("status") HypothesisStatus status);
}
