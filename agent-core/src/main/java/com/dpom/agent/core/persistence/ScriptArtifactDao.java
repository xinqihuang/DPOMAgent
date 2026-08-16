package com.dpom.agent.core.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

import com.dpom.agent.core.persistence.command.ScriptArtifactInsert;
import com.dpom.agent.core.script.ScriptArtifact;

/**
 * 脚本工件持久化 Mapper（MyBatis XML）。
 */
@Mapper
public interface ScriptArtifactDao {

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 脚本工件（可为空）
     */
    Optional<ScriptArtifact> findById(@Param("id") long id);

    /**
     * 按调查查询脚本工件列表。
     *
     * @param investigationId 调查 id
     * @return 脚本工件列表
     */
    List<ScriptArtifact> findByInvestigationId(@Param("investigationId") long investigationId);

    /**
     * 插入脚本工件，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(ScriptArtifactInsert command);
}
