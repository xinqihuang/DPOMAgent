package com.dpom.agent.core.persistence;

import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库连通性探针 Mapper（MyBatis XML）。
 */
@Mapper
public interface HealthCheckMapper {

    /**
     * 数据库连通性探测。
     *
     * @return 1
     */
    Integer ping();
}
