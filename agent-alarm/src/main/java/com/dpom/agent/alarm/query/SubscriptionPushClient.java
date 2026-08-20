package com.dpom.agent.alarm.query;

import com.dpom.agent.alarm.domain.Alarm;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 订阅推送客户端：将告警 JSON 异步推送到订阅方回调 URL，统一 Spring RestClient。
 */
@Component
public class SubscriptionPushClient {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionPushClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * 构造推送客户端。
     *
     * @param restClient  通知专用 RestClient
     * @param objectMapper JSON 序列化
     */
    public SubscriptionPushClient(@Qualifier("alarmNotificationRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 推送告警到回调 URL。
     *
     * @param callbackUrl 回调 URL
     * @param alarm       告警
     */
    public void pushTo(String callbackUrl, Alarm alarm) {
        try {
            String body = objectMapper.writeValueAsString(alarm);
            restClient.post().uri(callbackUrl).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .toBodilessEntity();
            LOG.info("订阅推送成功 url={} alarmId={}", callbackUrl, alarm.id());
        } catch (Exception e) {
            LOG.warn("订阅推送失败 url={}: {}", callbackUrl, e.getMessage());
        }
    }
}
