package com.dpom.agent.core.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.dpom.agent.core.investigation.InvestigationStep;
import com.dpom.agent.core.persistence.command.InvestigationStepInsert;

/**
 * 调查步骤持久化 Mapper（MyBatis XML，仅追加）。
 */
@Mapper
public interface InvestigationStepDao {

    /**
     * 查询某调查当前最大步骤序号（无步骤时为 0）。
     *
     * @param investigationId 调查 id
     * @return 最大步骤序号
     */
    int maxStepOrder(@Param("investigationId") long investigationId);

    /**
     * 按调查查询步骤（按序号升序）。
     *
     * @param investigationId 调查 id
     * @return 步骤列表
     */
    List<InvestigationStep> findByInvestigationId(@Param("investigationId") long investigationId);

    /**
     * 追加一步，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int append(InvestigationStepInsert command);
}
