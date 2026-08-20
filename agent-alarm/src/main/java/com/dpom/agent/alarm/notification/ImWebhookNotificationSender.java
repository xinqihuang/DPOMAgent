package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.domain.NotificationChannel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * IM webhook 通知发送器：POST JSON 到目标 webhook URL，统一 Spring RestClient。
 */
@Component
public class ImWebhookNotificationSender implements NotificationSender {

    private static final Logger LOG = LoggerFactory.getLogger(ImWebhookNotificationSender.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * 构造 IM webhook 发送器。
     *
     * @param restClient  通知专用 RestClient
     * @param objectMapper JSON 序列化
     */
    public ImWebhookNotificationSender(@Qualifier("alarmNotificationRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IM_WEBHOOK;
    }

    @Override
    public SendOutcome send(NotificationMessage message) {
        String webhookUrl = message.target().recipient();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return SendOutcome.fail("webhook URL 为空");
        }
        try {
            String body = toJson(message);
            restClient.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .toBodilessEntity();
            LOG.info("IM webhook 通知已发送 incidentId={} url={}", message.incidentId(), webhookUrl);
            return SendOutcome.ok();
        } catch (Exception e) {
            LOG.warn("IM webhook 通知发送失败 incidentId={}: {}", message.incidentId(), e.getMessage());
            return SendOutcome.fail(e.getMessage());
        }
    }

    private String toJson(NotificationMessage message) throws JsonProcessingException {
        Map<String, Object> payload = Map.of(
                "text", message.subject() + "\n" + message.body(),
                "incidentId", message.incidentId());
        return objectMapper.writeValueAsString(payload);
    }
}
