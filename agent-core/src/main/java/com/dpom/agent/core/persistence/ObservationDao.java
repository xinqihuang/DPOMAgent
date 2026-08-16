package com.dpom.agent.core.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

import com.dpom.agent.core.observation.Observation;
import com.dpom.agent.core.persistence.command.ObservationInsert;

/**
 * 观察（证据）持久化 Mapper（MyBatis XML）。
 */
@Mapper
public interface ObservationDao {

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 观察（可为空）
     */
    Optional<Observation> findById(@Param("id") long id);

    /**
     * 按调查查询观察列表。
     *
     * @param investigationId 调查 id
     * @return 观察列表
     */
    List<Observation> findByInvestigationId(@Param("investigationId") long investigationId);

    /**
     * 插入观察，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(ObservationInsert command);
}
