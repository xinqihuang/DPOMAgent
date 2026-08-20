package com.dpom.agent.alarm.persistence;

import com.dpom.agent.alarm.domain.AlarmIncident;
import com.dpom.agent.alarm.persistence.command.AlarmIncidentInsert;
import com.dpom.agent.common.alarm.AlarmIncidentStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 告警事件持久化 Mapper（MyBatis XML）。
 */
@Mapper
public interface AlarmIncidentDao {

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 事件（可为空）
     */
    Optional<AlarmIncident> findById(@Param("id") long id);

    /**
     * 插入事件，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(AlarmIncidentInsert command);

    /**
     * 添加事件成员告警。
     *
     * @param incidentId 事件 id
     * @param alarmId    告警 id
     * @return 受影响行数
     */
    int addMember(@Param("incidentId") long incidentId, @Param("alarmId") long alarmId);

    /**
     * 查询事件成员告警 id 列表。
     *
     * @param incidentId 事件 id
     * @return 告警 id 列表
     */
    List<Long> findMemberAlarmIds(@Param("incidentId") long incidentId);

    /**
     * 更新生命周期状态与认领/闭环时间。
     *
     * @param id            事件 id
     * @param status        新状态
     * @param assignee      处理人（可为空）
     * @param acknowledgedAt 认领时间（可为空）
     * @param resolvedAt    闭环时间（可为空）
     * @param endedAt       结束时间（可为空）
     * @param updatedAt     更新时间
     * @return 受影响行数
     */
    int updateLifecycle(@Param("id") long id, @Param("status") AlarmIncidentStatus status,
            @Param("assignee") String assignee, @Param("acknowledgedAt") LocalDateTime acknowledgedAt,
            @Param("resolvedAt") LocalDateTime resolvedAt, @Param("endedAt") LocalDateTime endedAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 更新升级候选标记与评估时间。
     *
     * @param id           事件 id
     * @param candidate    是否升级候选
     * @param evaluatedAt  评估时间
     * @param updatedAt    更新时间
     * @return 受影响行数
     */
    int updateEscalation(@Param("id") long id, @Param("candidate") boolean candidate,
            @Param("evaluatedAt") LocalDateTime evaluatedAt, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 分页查询事件（支持状态/严重度/服务/时间区间过滤 + keyset 游标）。
     *
     * @param query 查询参数
     * @return 事件列表（按 id 倒序，最多 limit 条）
     */
    List<AlarmIncident> search(AlarmIncidentQuery query);
}
