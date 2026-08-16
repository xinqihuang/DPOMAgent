package com.dpom.agent.core.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

import com.dpom.agent.core.persistence.command.EvidenceBundleInsert;

/**
 * 证据束持久化 Mapper（MyBatis XML）：保存/读取有界脱敏摘要 JSON。
 */
@Mapper
public interface EvidenceBundleDao {

    /**
     * 按调查查询最新证据束 JSON。
     *
     * @param id 调查 id
     * @return 证据束 JSON（可为空）
     */
    Optional<String> findBundleJson(@Param("id") long id);

    /**
     * 插入证据束，自增主键回填到 {@code command.id}。
     *
     * @param command 插入命令
     * @return 受影响行数
     */
    int insert(EvidenceBundleInsert command);
}
