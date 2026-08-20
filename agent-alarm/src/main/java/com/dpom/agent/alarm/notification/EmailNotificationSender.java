package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.domain.NotificationChannel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 邮件通知发送器：经配置的邮件网关 HTTP 接口发送，统一 Spring RestClient。
 *
 * <p>不直接连 SMTP；邮件网关地址由 {@code dpom.alarm.notification.email.gateway-url} 配置。</p>
 */
@Component
public class EmailNotificationSender implements NotificationSender {

    private static final Logger LOG = LoggerFactory.getLogger(EmailNotificationSender.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String gatewayUrl;

    /**
     * 构造邮件发送器。
     *
     * @param restClient  通知专用 RestClient
     * @param objectMapper JSON 序列化
     * @param gatewayUrl  邮件网关地址
     */
    public EmailNotificationSender(@Qualifier("alarmNotificationRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${dpom.alarm.notification.email.gateway-url:}") String gatewayUrl) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.gatewayUrl = gatewayUrl;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public SendOutcome send(NotificationMessage message) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            return SendOutcome.fail("邮件网关地址未配置");
        }
        try {
            String body = toJson(message);
            restClient.post().uri(gatewayUrl).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .toBodilessEntity();
            LOG.info("邮件通知已发送 incidentId={} recipient={}", message.incidentId(),
                    message.target().recipient());
            return SendOutcome.ok();
        } catch (Exception e) {
            LOG.warn("邮件通知发送失败 incidentId={}: {}", message.incidentId(), e.getMessage());
            return SendOutcome.fail(e.getMessage());
        }
    }

    private String toJson(NotificationMessage message) throws JsonProcessingException {
        Map<String, Object> payload = Map.of(
                "to", message.target().recipient(),
                "subject", message.subject(),
                "body", message.body());
        return objectMapper.writeValueAsString(payload);
    }
}
