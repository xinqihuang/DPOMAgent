package com.dpom.agent.core.persistence;

import com.dpom.agent.core.diagnosisevent.DiagnosisEventReplayNonce;
import com.dpom.agent.core.persistence.command.DiagnosisReplayNonceInsert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 持久化重放 nonce MyBatis XML Mapper。
 */
@Mapper
public interface DiagnosisReplayNonceDao {
    int insert(DiagnosisReplayNonceInsert command);
    Optional<DiagnosisEventReplayNonce> findActive(@Param("nonce") String nonce, @Param("now") LocalDateTime now);
    boolean existsActive(@Param("nonce") String nonce, @Param("now") LocalDateTime now);
    int deleteExpired(@Param("now") LocalDateTime now);
}
