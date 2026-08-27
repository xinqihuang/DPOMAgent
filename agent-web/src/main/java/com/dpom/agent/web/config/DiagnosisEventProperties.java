package com.dpom.agent.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.nio.charset.StandardCharsets;

/**
 * Diagnosis Event 外发与内部重放的安全边界配置。
 */
@ConfigurationProperties(prefix = "dpom.evaluation")
public class DiagnosisEventProperties {

    private final Delivery delivery = new Delivery();
    private final Replay replay = new Replay();

    public Delivery getDelivery() { return delivery; }
    public Replay getReplay() { return replay; }

    /** 校验启用的投递配置完整且有界。 */
    public void validateDelivery() {
        if (!delivery.enabled) {
            return;
        }
        URI uri = delivery.destination;
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException("INVALID_HTTPS_DESTINATION");
        }
        requireStrongSecret(delivery.hmacSecret, "WEAK_DELIVERY_HMAC_SECRET");
        if (delivery.maxAttempts < 1 || delivery.batchSize < 1 || delivery.maxAcknowledgementBytes < 1
                || !positive(delivery.maxEventAge) || !positive(delivery.baseDelay) || !positive(delivery.maxDelay)
                || !positive(delivery.leaseDuration) || !positive(delivery.connectTimeout)
                || !positive(delivery.readTimeout) || !positive(delivery.pollDelay)
                || delivery.baseDelay.compareTo(delivery.maxDelay) > 0) {
            throw new IllegalStateException("INVALID_DELIVERY_BOUNDS");
        }
    }

    /** 校验启用的重放认证配置。 */
    public void validateReplay() {
        if (!replay.enabled) {
            return;
        }
        requireStrongSecret(replay.hmacSecret, "WEAK_REPLAY_HMAC_SECRET");
        if (!positive(replay.timestampWindow) || !positive(replay.nonceTtl)
                || replay.maxOperatorRef < 1 || replay.maxReason < 1) {
            throw new IllegalStateException("INVALID_REPLAY_BOUNDS");
        }
    }

    private void requireStrongSecret(String value, String errorCode) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(errorCode);
        }
    }

    private boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }

    /** 外部投递配置，默认关闭。 */
    public static class Delivery {
        private boolean enabled;
        private URI destination;
        private String hmacSecret = "";
        private int maxAttempts = 5;
        private Duration maxEventAge = Duration.ofDays(1);
        private Duration baseDelay = Duration.ofSeconds(1);
        private Duration maxDelay = Duration.ofMinutes(5);
        private Duration leaseDuration = Duration.ofSeconds(30);
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);
        private Duration pollDelay = Duration.ofSeconds(1);
        private int batchSize = 20;
        private int maxAcknowledgementBytes = 4096;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public URI getDestination() { return destination; }
        public void setDestination(URI destination) { this.destination = destination; }
        public String getHmacSecret() { return hmacSecret; }
        public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public Duration getMaxEventAge() { return maxEventAge; }
        public void setMaxEventAge(Duration maxEventAge) { this.maxEventAge = maxEventAge; }
        public Duration getBaseDelay() { return baseDelay; }
        public void setBaseDelay(Duration baseDelay) { this.baseDelay = baseDelay; }
        public Duration getMaxDelay() { return maxDelay; }
        public void setMaxDelay(Duration maxDelay) { this.maxDelay = maxDelay; }
        public Duration getLeaseDuration() { return leaseDuration; }
        public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
        public Duration getPollDelay() { return pollDelay; }
        public void setPollDelay(Duration pollDelay) { this.pollDelay = pollDelay; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getMaxAcknowledgementBytes() { return maxAcknowledgementBytes; }
        public void setMaxAcknowledgementBytes(int value) { this.maxAcknowledgementBytes = value; }
    }

    /** 内部重放认证配置，默认关闭。 */
    public static class Replay {
        private boolean enabled;
        private String hmacSecret = "";
        private Duration timestampWindow = Duration.ofMinutes(5);
        private Duration nonceTtl = Duration.ofMinutes(10);
        private int maxOperatorRef = 128;
        private int maxReason = 512;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHmacSecret() { return hmacSecret; }
        public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }
        public Duration getTimestampWindow() { return timestampWindow; }
        public void setTimestampWindow(Duration value) { this.timestampWindow = value; }
        public Duration getNonceTtl() { return nonceTtl; }
        public void setNonceTtl(Duration nonceTtl) { this.nonceTtl = nonceTtl; }
        public int getMaxOperatorRef() { return maxOperatorRef; }
        public void setMaxOperatorRef(int value) { this.maxOperatorRef = value; }
        public int getMaxReason() { return maxReason; }
        public void setMaxReason(int maxReason) { this.maxReason = maxReason; }
    }
}
