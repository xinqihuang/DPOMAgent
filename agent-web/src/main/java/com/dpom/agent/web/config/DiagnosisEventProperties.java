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
        if (delivery.mode == null) {
            throw new IllegalStateException("MISSING_DELIVERY_MODE");
        }
        if (delivery.mode == DeliveryMode.KAFKA) {
            validateKafkaDelivery();
        } else {
            validateHttpDelivery();
        }
        if (delivery.maxAttempts < 1 || delivery.batchSize < 1 || delivery.maxAcknowledgementBytes < 1
                || delivery.maxBacklog < delivery.batchSize || delivery.maxBacklog > 1_000_000
                || !positive(delivery.maxEventAge) || !positive(delivery.baseDelay) || !positive(delivery.maxDelay)
                || !positive(delivery.leaseDuration) || !positive(delivery.connectTimeout)
                || !positive(delivery.readTimeout) || !positive(delivery.pollDelay)
                || delivery.baseDelay.compareTo(delivery.maxDelay) > 0) {
            throw new IllegalStateException("INVALID_DELIVERY_BOUNDS");
        }
    }

    private void validateHttpDelivery() {
        URI uri = delivery.destination;
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException("INVALID_HTTPS_DESTINATION");
        }
        requireStrongSecret(delivery.hmacSecret, "WEAK_DELIVERY_HMAC_SECRET");
    }

    private void validateKafkaDelivery() {
        Kafka kafka = delivery.kafka;
        if (kafka.bootstrapServers == null || kafka.bootstrapServers.isEmpty()
                || !"dpom.diagnosis-event.v2".equals(kafka.topic)
                || !identifier(kafka.producerIdentity) || !positive(kafka.acknowledgementTimeout)) {
            throw new IllegalStateException("INVALID_KAFKA_DELIVERY_CONFIG");
        }
    }

    private boolean identifier(String value) {
        return value != null && value.length() <= 128
                && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*");
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
        private DeliveryMode mode = DeliveryMode.HTTP;
        private URI destination;
        private String hmacSecret = "";
        private final Kafka kafka = new Kafka();
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
        private int maxBacklog = 10_000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public DeliveryMode getMode() { return mode; }
        public void setMode(DeliveryMode mode) { this.mode = mode; }
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
        public int getMaxBacklog() { return maxBacklog; }
        public void setMaxBacklog(int value) { maxBacklog = value; }
        public Kafka getKafka() { return kafka; }
    }

    /** 单一活动传输，切换不会改变冻结事件。 */
    public enum DeliveryMode { HTTP, KAFKA }

    /** Kafka v2 生产者边界，默认没有 broker 地址且不可装配。 */
    public static class Kafka {
        private java.util.List<String> bootstrapServers = java.util.List.of();
        private String topic = "dpom.diagnosis-event.v2";
        private String producerIdentity = "";
        private Duration acknowledgementTimeout = Duration.ofSeconds(5);

        public java.util.List<String> getBootstrapServers() { return java.util.List.copyOf(bootstrapServers); }
        public void setBootstrapServers(java.util.List<String> value) {
            bootstrapServers = value == null ? java.util.List.of() : java.util.List.copyOf(value);
        }
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public String getProducerIdentity() { return producerIdentity; }
        public void setProducerIdentity(String value) { producerIdentity = value; }
        public Duration getAcknowledgementTimeout() { return acknowledgementTimeout; }
        public void setAcknowledgementTimeout(Duration value) { acknowledgementTimeout = value; }
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
