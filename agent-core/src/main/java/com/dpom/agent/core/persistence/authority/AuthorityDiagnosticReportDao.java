package com.dpom.agent.core.persistence.authority;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/** Diagnosis-only 报告 head 与不可变修订的 MyBatis 端口。 */
@Mapper
public interface AuthorityDiagnosticReportDao {
    Optional<DiagnosticReportHeadRow> findHead(@Param("investigationId") String investigationId);
    Optional<DiagnosticReportRevisionRow> findRevision(@Param("reportId") String reportId);
    Optional<DiagnosticReportRevisionRow> findByRequestKey(@Param("investigationId") String investigationId,
                                                           @Param("requestKey") String requestKey);
    List<DiagnosticReportRevisionRow> findHistoryPage(@Param("investigationId") String investigationId,
                                                      @Param("afterRevision") long afterRevision,
                                                      @Param("limit") int limit);
    int insertHead(DiagnosticReportHeadRow row);
    int advanceHead(@Param("investigationId") String investigationId,
                    @Param("expectedRevision") long expectedRevision,
                    @Param("expectedLockVersion") long expectedLockVersion,
                    @Param("latestReportId") String latestReportId,
                    @Param("updatedAt") java.time.LocalDateTime updatedAt);
    int insertRevision(DiagnosticReportRevisionRow row);
}
