package com.dpom.agent.alarm.query;

import com.dpom.agent.alarm.domain.Alarm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 告警订阅注册与异步推送：治理完成后推送匹配告警到各订阅回调。
 *
 * <p>推送在虚拟线程上异步执行，不阻塞接入/治理路径。注册/推送线程安全。</p>
 */
@Service
public class AlarmSubscriptionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AlarmSubscriptionRegistry.class);

    private final List<AlarmSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ExecutorService executor;

    /**
     * 构造订阅注册表（虚拟线程执行器）。
     */
    public AlarmSubscriptionRegistry() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 注册订阅。
     *
     * @param subscription 订阅
     */
    public void register(AlarmSubscription subscription) {
        subscriptions.add(subscription);
        LOG.info("注册告警订阅，当前订阅数={}", subscriptions.size());
    }

    /**
     * 治理完成后异步推送告警到所有匹配订阅。
     *
     * @param alarm 治理完成后的告警
     */
    public void publish(Alarm alarm) {
        for (AlarmSubscription sub : subscriptions) {
            if (sub.matches(alarm)) {
                executor.submit(() -> safeAccept(sub, alarm));
            }
        }
    }

    private void safeAccept(AlarmSubscription sub, Alarm alarm) {
        try {
            sub.callback().accept(alarm);
        } catch (Exception e) {
            LOG.warn("订阅回调执行失败: {}", e.getMessage());
        }
    }

    /**
     * 返回当前订阅数（测试用）。
     *
     * @return 订阅数
     */
    public int size() {
        return subscriptions.size();
    }
}
