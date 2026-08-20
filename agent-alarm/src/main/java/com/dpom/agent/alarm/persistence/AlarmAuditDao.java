package com.dpom.agent.alarm.persistence;

import com.dpom.agent.alarm.domain.AlarmAudit;
import com.dpom.agent.alarm.persistence.command.AlarmAuditInsert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 告警审计持久化 Mapper（MyBatis XML）。
 */
@Mapper
public interface AlarmAuditDao {

    /**
     * 插入审计条目，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(AlarmAuditInsert command);

    /**
     * 按目标查询审计时间线。
     *
     * @param targetType 目标类型
     * @param targetId   目标 id
     * @return 审计条目列表（按时间升序）
     */
    List<AlarmAudit> findByTarget(@Param("targetType") String targetType, @Param("targetId") long targetId);
}
