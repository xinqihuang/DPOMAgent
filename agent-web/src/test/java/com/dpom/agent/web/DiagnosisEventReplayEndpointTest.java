package com.dpom.agent.web;

import com.dpom.agent.core.diagnosisevent.DiagnosisEventOutbox;
import com.dpom.agent.core.diagnosisevent.DiagnosisEventStateService;
import com.dpom.agent.core.diagnosisevent.DiagnosisOutboxStatus;
import com.dpom.agent.core.persistence.DiagnosisEventOutboxDao;
import com.dpom.agent.core.persistence.command.DiagnosisEventLeaseCommand;
import com.dpom.agent.core.persistence.command.DiagnosisEventOutboxInsert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 显式启用后的内部重放端点端到端测试。
 */
@SpringBootTest(properties = {
        "dpom.evaluation.replay.enabled=true",
        "dpom.evaluation.replay.hmac-secret=abcdef0123456789abcdef0123456789"
})
@AutoConfigureMockMvc
class DiagnosisEventReplayEndpointTest {

    private static final byte[] SECRET = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);
    private static final String PATH = "/internal/v1/diagnosis-events/replay";

    @Autowired private MockMvc mockMvc;
    @Autowired private DiagnosisEventOutboxDao outboxDao;
    @Autowired private DiagnosisEventStateService stateService;
    @Autowired private Clock clock;

    @Test
    void authenticatedRequestReplaysStoredDeadEventWithoutReplacementContent() throws Exception {
        String content = "{\"immutable\":true}";
        String eventId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        DiagnosisEventOutboxInsert insert = new DiagnosisEventOutboxInsert(eventId, "idem-" + UUID.randomUUID(),
                1801, 2801, "investigation.completed", 1, "1.0", content, sha256(content), now.minusSeconds(1));
        outboxDao.insert(insert);
        assertThat(outboxDao.acquireLease(new DiagnosisEventLeaseCommand(
                insert.getId(), now, "worker-endpoint", "token-endpoint", now.plusMinutes(1)))).isOne();
        DiagnosisEventOutbox leased = outboxDao.findById(insert.getId()).orElseThrow();
        stateService.markDead(leased, "PERMANENT_REJECTION", "HTTP_400", now.plusSeconds(1));
        byte[] body = ("{\"eventId\":\"" + eventId
                + "\",\"operatorRef\":\"operator-1\",\"reason\":\"downstream fixed\"}")
                .getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        String nonce = "nonce_" + UUID.randomUUID().toString().replace("-", "");

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-DPOM-Timestamp", timestamp)
                        .header("X-DPOM-Nonce", nonce)
                        .header("X-DPOM-Signature", signature(timestamp, nonce, body)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        DiagnosisEventOutbox replayed = outboxDao.findByEventId(eventId).orElseThrow();
        assertThat(replayed.status()).isEqualTo(DiagnosisOutboxStatus.PENDING);
        assertThat(replayed.canonicalContent()).isEqualTo(content);
        assertThat(replayed.canonicalSha256()).isEqualTo(insert.getCanonicalSha256());
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String signature(String timestamp, String nonce, byte[] body) {
        try {
            String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            String input = timestamp + '\n' + "POST" + '\n' + PATH + '\n' + nonce + '\n' + bodyHash;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
