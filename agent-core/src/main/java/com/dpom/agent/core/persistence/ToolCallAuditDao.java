package com.dpom.agent.core.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.dpom.agent.core.persistence.command.ToolCallAuditInsert;
import com.dpom.agent.core.tool.ToolCallAudit;

/**
 * 工具调用审计持久化 Mapper（MyBatis XML，仅追加）。
 */
@Mapper
public interface ToolCallAuditDao {

    /**
     * 按调查查询审计记录列表。
     *
     * @param investigationId 调查 id
     * @return 审计记录列表
     */
    List<ToolCallAudit> findByInvestigationId(@Param("investigationId") long investigationId);

    /**
     * 追加审计记录，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int append(ToolCallAuditInsert command);
}
