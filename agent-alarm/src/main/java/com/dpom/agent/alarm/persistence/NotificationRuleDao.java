package com.dpom.agent.alarm.persistence;

import com.dpom.agent.alarm.domain.NotificationRule;
import com.dpom.agent.alarm.persistence.command.NotificationRuleInsert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 通知规则持久化 Mapper（MyBatis XML）。
 */
@Mapper
public interface NotificationRuleDao {

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 规则（可为空）
     */
    Optional<NotificationRule> findById(@Param("id") long id);

    /**
     * 查询所有启用规则。
     *
     * @return 启用规则列表
     */
    List<NotificationRule> findAllEnabled();

    /**
     * 查询全部规则（含停用），按 id 升序，用于管理面。
     *
     * @return 全部规则列表
     */
    List<NotificationRule> findAll();

    /**
     * 插入规则，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(NotificationRuleInsert command);

    /**
     * 更新启用状态。
     *
     * @param id      主键
     * @param enabled 是否启用
     * @return 受影响行数
     */
    int updateEnabled(@Param("id") long id, @Param("enabled") boolean enabled);
}
