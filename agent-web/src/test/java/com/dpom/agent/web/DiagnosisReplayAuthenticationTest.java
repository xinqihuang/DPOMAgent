package com.dpom.agent.web;

import com.dpom.agent.core.persistence.DiagnosisReplayNonceDao;
import com.dpom.agent.web.diagnosisevent.DiagnosisReplayAuthenticator;
import com.dpom.agent.web.diagnosisevent.DiagnosisReplayRequestValidator;
import com.dpom.agent.web.diagnosisevent.ReplayAuthenticationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内部重放 HMAC、持久化 nonce 和请求 allow-list 测试。
 */
@SpringBootTest
class DiagnosisReplayAuthenticationTest {

    private static final byte[] SECRET = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-08-21T06:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Autowired private DiagnosisReplayNonceDao nonceDao;

    @Test
    void validSignatureConsumesPersistentNonceAcrossAuthenticatorRestart() {
        byte[] body = validBody();
        String timestamp = Long.toString(NOW.getEpochSecond());
        String nonce = "nonce_" + UUID.randomUUID().toString().replace("-", "");
        String signature = signature(timestamp, nonce, body);
        authenticator().authenticate(timestamp, nonce, signature, body);

        assertThatThrownBy(() -> authenticator().authenticate(timestamp, nonce, signature, body))
                .isInstanceOf(ReplayAuthenticationException.class)
                .hasMessage("REPLAY_AUTHENTICATION_FAILED");
    }

    @Test
    void staleFutureAndDifferentSignaturePositionsHaveUniformErrors() {
        byte[] body = validBody();
        String nonce = "nonce_" + UUID.randomUUID().toString().replace("-", "");
        assertUniform(Long.toString(NOW.minusSeconds(301).getEpochSecond()), nonce, "sha256=" + "0".repeat(64), body);
        assertUniform(Long.toString(NOW.plusSeconds(301).getEpochSecond()), nonce, "sha256=" + "0".repeat(64), body);
        String timestamp = Long.toString(NOW.getEpochSecond());
        assertUniform(timestamp, nonce, "sha256=0" + "a".repeat(63), body);
        assertUniform(timestamp, nonce, "sha256=" + "a".repeat(63) + "0", body);
    }

    @Test
    void requestValidatorRejectsUnknownFieldsAndBoundsOperatorAndReason() {
        DiagnosisReplayRequestValidator validator = new DiagnosisReplayRequestValidator(new ObjectMapper(), 16, 32);
        assertThat(validator.validate(validBody())).extracting("operatorRef", "reason")
                .containsExactly("operator-1", "retry after downstream fix");
        assertThatThrownBy(() -> validator.validate((new String(validBody(), StandardCharsets.UTF_8)
                .replace("}", ",\"content\":\"replacement\"}" )).getBytes(StandardCharsets.UTF_8)))
                .hasMessage("INVALID_REPLAY_REQUEST");
        assertThatThrownBy(() -> validator.validate(body("operator-reference-too-long", "valid reason")))
                .hasMessage("INVALID_REPLAY_REQUEST");
        assertThatThrownBy(() -> validator.validate(body("operator-1", "x".repeat(33))))
                .hasMessage("INVALID_REPLAY_REQUEST");
    }

    private DiagnosisReplayAuthenticator authenticator() {
        return new DiagnosisReplayAuthenticator(nonceDao, SECRET, Duration.ofMinutes(5),
                Duration.ofMinutes(10), CLOCK);
    }

    private void assertUniform(String timestamp, String nonce, String signature, byte[] body) {
        assertThatThrownBy(() -> authenticator().authenticate(timestamp, nonce, signature, body))
                .isInstanceOf(ReplayAuthenticationException.class)
                .hasMessage("REPLAY_AUTHENTICATION_FAILED");
    }

    private byte[] validBody() {
        return body("operator-1", "retry after downstream fix");
    }

    private byte[] body(String operatorRef, String reason) {
        String value = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"operatorRef\":\""
                + operatorRef + "\",\"reason\":\"" + reason + "\"}";
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String signature(String timestamp, String nonce, byte[] body) {
        try {
            String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            String input = timestamp + '\n' + "POST" + '\n'
                    + "/internal/v1/diagnosis-events/replay" + '\n' + nonce + '\n' + bodyHash;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
