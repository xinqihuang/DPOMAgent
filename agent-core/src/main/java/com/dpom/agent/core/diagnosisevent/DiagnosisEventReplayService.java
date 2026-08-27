package com.dpom.agent.core.diagnosisevent;

import com.dpom.agent.core.persistence.DiagnosisEventAuditDao;
import com.dpom.agent.core.persistence.DiagnosisEventOutboxDao;
import com.dpom.agent.core.persistence.command.DiagnosisEventAuditInsert;
import com.dpom.agent.core.persistence.command.DiagnosisEventReplayCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 保持原始身份和内容不变地将 DEAD 事件重置为 PENDING。
 */
@Service
public class DiagnosisEventReplayService {

    private final DiagnosisEventOutboxDao outboxDao;
    private final DiagnosisEventAuditDao auditDao;
    private final Clock clock;

    /** 创建重放服务。 */
    public DiagnosisEventReplayService(DiagnosisEventOutboxDao outboxDao, DiagnosisEventAuditDao auditDao, Clock clock) {
        this.outboxDao = outboxDao;
        this.auditDao = auditDao;
        this.clock = clock;
    }

    /** 原子重置事件并追加操作者审计。 */
    @Transactional
    public DiagnosisEventOutbox replay(String eventId, String operatorRef, String reason) {
        DiagnosisEventOutbox event = outboxDao.findByEventId(eventId)
                .orElseThrow(() -> new DiagnosisReplayException("REPLAY_EVENT_NOT_FOUND"));
        if (event.status() != DiagnosisOutboxStatus.DEAD) {
            throw new DiagnosisReplayException("REPLAY_EVENT_NOT_DEAD");
        }
        if (!contentIntact(event)) {
            throw new DiagnosisReplayException("CONTENT_INTEGRITY_FAILURE");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (outboxDao.replay(new DiagnosisEventReplayCommand(event.id(), now)) != 1) {
            throw new DiagnosisReplayException("REPLAY_RACE");
        }
        auditDao.append(new DiagnosisEventAuditInsert(event.eventId(), event.eventType(), "OPERATOR_REPLAY",
                "SUCCESS", null, operatorRef, reason, event.eventId()));
        return outboxDao.findById(event.id()).orElseThrow();
    }

    private boolean contentIntact(DiagnosisEventOutbox event) {
        try {
            byte[] content = event.canonicalContent().getBytes(StandardCharsets.UTF_8);
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(content);
            byte[] expected = HexFormat.of().parseHex(event.canonicalSha256());
            return MessageDigest.isEqual(actual, expected);
        } catch (RuntimeException | java.security.NoSuchAlgorithmException exception) {
            return false;
        }
    }
}
