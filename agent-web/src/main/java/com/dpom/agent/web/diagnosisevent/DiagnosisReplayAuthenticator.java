package com.dpom.agent.web.diagnosisevent;

import com.dpom.agent.core.persistence.DiagnosisReplayNonceDao;
import com.dpom.agent.core.persistence.command.DiagnosisReplayNonceInsert;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

/**
 * 校验时间窗、HMAC 和持久化一次性 nonce 的重放认证器。
 */
public final class DiagnosisReplayAuthenticator {

    private final DiagnosisReplayNonceDao nonceDao;
    private final byte[] secret;
    private final Duration timestampWindow;
    private final Duration nonceTtl;
    private final Clock clock;

    /** 创建认证器。 */
    public DiagnosisReplayAuthenticator(DiagnosisReplayNonceDao nonceDao, byte[] secret,
                                        Duration timestampWindow, Duration nonceTtl, Clock clock) {
        this.nonceDao = nonceDao;
        this.secret = secret.clone();
        this.timestampWindow = timestampWindow;
        this.nonceTtl = nonceTtl;
        this.clock = clock;
    }

    /** 认证固定方法和路径的原始请求体，并消费 nonce。 */
    public void authenticate(String timestamp, String nonce, String signature, byte[] body) {
        try {
            Instant signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
            Instant nowInstant = clock.instant();
            if (Duration.between(signedAt, nowInstant).abs().compareTo(timestampWindow) > 0
                    || nonce == null || !nonce.matches("[A-Za-z0-9_-]{16,128}")) {
                throw new ReplayAuthenticationException();
            }
            byte[] supplied = decodeSignature(signature);
            byte[] expected = signature(timestamp, nonce, body);
            if (!MessageDigest.isEqual(expected, supplied)) {
                throw new ReplayAuthenticationException();
            }
            LocalDateTime now = LocalDateTime.ofInstant(nowInstant, ZoneOffset.UTC);
            if (nonceDao.existsActive(nonce, now)) {
                throw new ReplayAuthenticationException();
            }
            nonceDao.insert(new DiagnosisReplayNonceInsert(nonce, now.plus(nonceTtl)));
        } catch (ReplayAuthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ReplayAuthenticationException();
        }
    }

    private byte[] decodeSignature(String value) {
        if (value == null || !value.matches("sha256=[0-9a-f]{64}")) {
            throw new ReplayAuthenticationException();
        }
        return HexFormat.of().parseHex(value.substring(7));
    }

    private byte[] signature(String timestamp, String nonce, byte[] body) {
        try {
            String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            String input = timestamp + '\n' + "POST" + '\n'
                    + "/internal/v1/diagnosis-events/replay" + '\n' + nonce + '\n' + bodyHash;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new ReplayAuthenticationException();
        }
    }
}
