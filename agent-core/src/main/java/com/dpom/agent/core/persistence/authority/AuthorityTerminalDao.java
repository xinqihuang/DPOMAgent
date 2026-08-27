package com.dpom.agent.core.persistence.authority;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/** 终态诊断源与发布意图 Mapper。 */
@Mapper
public interface AuthorityTerminalDao {

    /** 插入不可变诊断源。 */
    int insertSource(DiagnosisSourceRow row);

    /** 插入同事务发布意图。 */
    int insertIntent(PublicationIntentRow row);

    /** 按 Investigation 读取唯一诊断源。 */
    Optional<DiagnosisSourceRow> findSource(@Param("investigationId") String investigationId);

    /** 统计可发布意图。 */
    int countPendingIntents(@Param("investigationId") String investigationId);
}

