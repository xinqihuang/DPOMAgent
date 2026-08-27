package com.dpom.agent.web.diagnosisevent;

import com.dpom.agent.core.diagnosisevent.DiagnosisEventOutbox;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventReplayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 单独开关保护的内部 Diagnosis Event 重放端点。
 */
@RestController
@ConditionalOnProperty(name = "dpom.evaluation.replay.enabled", havingValue = "true")
public class DiagnosisEventReplayController {

    public static final String PATH = "/internal/v1/diagnosis-events/replay";
    private final DiagnosisReplayAuthenticator authenticator;
    private final DiagnosisReplayRequestValidator validator;
    private final DiagnosisEventReplayService replayService;

    /** 创建内部重放端点。 */
    public DiagnosisEventReplayController(DiagnosisReplayAuthenticator authenticator,
                                          DiagnosisReplayRequestValidator validator,
                                          DiagnosisEventReplayService replayService) {
        this.authenticator = authenticator;
        this.validator = validator;
        this.replayService = replayService;
    }

    /** 认证原始请求并重置指定 DEAD 事件。 */
    @RequestMapping(path = PATH, method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> replay(
            @RequestHeader("X-DPOM-Timestamp") String timestamp,
            @RequestHeader("X-DPOM-Nonce") String nonce,
            @RequestHeader("X-DPOM-Signature") String signature,
            @RequestBody byte[] body) {
        authenticator.authenticate(timestamp, nonce, signature, body);
        DiagnosisReplayRequest request = validator.validate(body);
        DiagnosisEventOutbox replayed = replayService.replay(
                request.eventId(), request.operatorRef(), request.reason());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("eventId", replayed.eventId(), "status", replayed.status().name()));
    }
}
