package com.dpom.agent.alarm.persistence;

import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.persistence.command.AlarmInsert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 告警持久化 Mapper（MyBatis XML）。
 */
@Mapper
public interface AlarmDao {

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 告警（可为空）
     */
    Optional<Alarm> findById(@Param("id") long id);

    /**
     * 按指纹查询最近一条告警（去重合并依据）。
     *
     * @param fingerprint 指纹
     * @return 告警（可为空）
     */
    Optional<Alarm> findLatestByFingerprint(@Param("fingerprint") String fingerprint);

    /**
     * 插入告警，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(AlarmInsert command);

    /**
     * 合并重复发生：递增发生计数、更新最近发生时间与压缩采样。
     *
     * @param id             告警 id
     * @param lastOccurredAt 最近发生时间
     * @param samplePayloads 压缩采样（可为空，空时不覆盖）
     * @return 受影响行数
     */
    int mergeOccurrence(@Param("id") long id, @Param("lastOccurredAt") LocalDateTime lastOccurredAt,
                        @Param("samplePayloads") String samplePayloads);

    /**
     * 分页查询告警（支持来源/资源/服务/严重度/状态/时间区间过滤 + keyset 游标）。
     *
     * @param query 查询参数
     * @return 告警列表（按 id 倒序，最多 limit 条）
     */
    List<Alarm> search(AlarmQuery query);
}
