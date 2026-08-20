package com.dpom.agent.alarm.persistence;

import com.dpom.agent.alarm.domain.NotificationRecord;
import com.dpom.agent.alarm.persistence.command.NotificationRecordInsert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通知记录持久化 Mapper（MyBatis XML）。
 */
@Mapper
public interface NotificationRecordDao {

    /**
     * 插入通知记录，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(NotificationRecordInsert command);

    /**
     * 按事件 id 查询通知记录。
     *
     * @param incidentId 事件 id
     * @return 通知记录列表
     */
    List<NotificationRecord> findByIncidentId(@Param("incidentId") long incidentId);
}
