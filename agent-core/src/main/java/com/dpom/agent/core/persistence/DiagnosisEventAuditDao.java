package com.dpom.agent.core.persistence;

import com.dpom.agent.core.diagnosisevent.DiagnosisEventAudit;
import com.dpom.agent.core.persistence.command.DiagnosisEventAuditInsert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Diagnosis Event 追加式审计 MyBatis XML Mapper。
 */
@Mapper
public interface DiagnosisEventAuditDao {
    List<DiagnosisEventAudit> findByEventId(@Param("eventId") String eventId);
    int append(DiagnosisEventAuditInsert command);
}
