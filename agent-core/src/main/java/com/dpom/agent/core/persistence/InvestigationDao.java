package com.dpom.agent.core.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.command.InvestigationInsert;

/**
 * 调查持久化 Mapper（MyBatis XML）。
 */
@Mapper
public interface InvestigationDao {

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 调查（可为空）
     */
    Optional<Investigation> findById(@Param("id") long id);

    /**
     * 按事件查询调查列表。
     *
     * @param incidentId 事件 id
     * @return 调查列表
     */
    List<Investigation> findByIncidentId(@Param("incidentId") long incidentId);

    /**
     * 查询非终态调查。
     *
     * @return 非终态调查列表
     */
    List<Investigation> findNonTerminal();

    /**
     * 插入调查，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(InvestigationInsert command);

    /**
     * 更新调查状态。
     *
     * @param id     主键
     * @param status 新状态
     */
    void updateStatus(@Param("id") long id, @Param("status") InvestigationStatus status);

    /**
     * 仅当调查仍处于活动状态时更新状态。
     *
     * @param id     主键
     * @param status 新状态
     * @return 受影响行数
     */
    int updateStatusIfActive(@Param("id") long id, @Param("status") InvestigationStatus status);

    /**
     * 更新当前 Run。
     *
     * @param id           主键
     * @param currentRunId 当前 Run id
     */
    void updateCurrentRun(@Param("id") long id, @Param("currentRunId") Long currentRunId);
}
