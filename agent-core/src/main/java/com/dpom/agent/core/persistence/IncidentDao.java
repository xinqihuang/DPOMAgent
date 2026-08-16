package com.dpom.agent.core.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.persistence.command.IncidentInsert;

/**
 * 事件持久化 Mapper（MyBatis XML）。
 */
@Mapper
public interface IncidentDao {

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 事件（可为空）
     */
    Optional<Incident> findById(@Param("id") long id);

    /**
     * 按服务编码与环境查询。
     *
     * @param serviceCode 服务编码
     * @param environment 环境
     * @return 事件（可为空）
     */
    Optional<Incident> findByServiceCodeAndEnvironment(@Param("serviceCode") String serviceCode,
                                                       @Param("environment") String environment);

    /**
     * 插入事件，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(IncidentInsert command);
}
