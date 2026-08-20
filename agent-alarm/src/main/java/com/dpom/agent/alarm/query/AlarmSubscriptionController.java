package com.dpom.agent.alarm.query;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警订阅 REST API：注册过滤条件与回调 URL，治理完成后异步推送匹配告警。
 */
@RestController
public class AlarmSubscriptionController {

    private final AlarmSubscriptionRegistry registry;
    private final SubscriptionPushClient pushClient;

    /**
     * 构造订阅控制器。
     *
     * @param registry   订阅注册表
     * @param pushClient 推送客户端
     */
    public AlarmSubscriptionController(AlarmSubscriptionRegistry registry, SubscriptionPushClient pushClient) {
        this.registry = registry;
        this.pushClient = pushClient;
    }

    /**
     * 注册订阅。
     *
     * @param request 订阅请求
     * @return 注册结果
     */
    @PostMapping("/api/v1/alarms/subscriptions")
    public SubscriptionResponse register(@RequestBody SubscriptionRequest request) {
        AlarmSubscription subscription = new AlarmSubscription(request.source(), request.serviceCode(),
                request.severity(), alarm -> pushClient.pushTo(request.callbackUrl(), alarm));
        registry.register(subscription);
        return new SubscriptionResponse("registered", registry.size());
    }

    /**
     * 订阅请求。
     *
     * @param source      来源过滤
     * @param serviceCode 服务过滤
     * @param severity    严重度过滤
     * @param callbackUrl 回调 URL
     */
    public record SubscriptionRequest(AlarmSource source, String serviceCode, SeverityLevel severity,
                                      String callbackUrl) {
    }

    /**
     * 订阅响应。
     *
     * @param status   状态
     * @param totalSubscriptions 当前总订阅数
     */
    public record SubscriptionResponse(String status, int totalSubscriptions) {
    }
}
