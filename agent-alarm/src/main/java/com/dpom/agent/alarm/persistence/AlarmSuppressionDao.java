package com.dpom.agent.alarm.persistence;

import com.dpom.agent.alarm.domain.AlarmSuppression;
import com.dpom.agent.alarm.persistence.command.AlarmSuppressionInsert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警抑制/静默持久化 Mapper（MyBatis XML）。
 */
@Mapper
public interface AlarmSuppressionDao {

    /**
     * 插入抑制记录，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(AlarmSuppressionInsert command);

    /**
     * 查询在指定时间点活跃的、匹配指定键的抑制记录。
     *
     * @param matchKey 匹配键
     * @param now      当前时间
     * @return 活跃抑制列表
     */
    List<AlarmSuppression> findActiveByMatchKey(@Param("matchKey") String matchKey,
                                                @Param("now") LocalDateTime now);
}
