package com.dpom.agent.alarm.query;

import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.AlarmStatus;
import com.dpom.agent.common.alarm.SeverityLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订阅注册表单测：异步推送、过滤匹配、不阻塞发布者。
 */
class AlarmSubscriptionRegistryTest {

    @Test
    void publishInvokesMatchingSubscriptionAsync() throws Exception {
        AlarmSubscriptionRegistry registry = new AlarmSubscriptionRegistry();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger received = new AtomicInteger();
        registry.register(new AlarmSubscription(AlarmSource.AOM, null, SeverityLevel.CRITICAL,
                a -> {
                    received.set(a.id().intValue());
                    latch.countDown();
                }));

        registry.publish(alarm(7L, AlarmSource.AOM, SeverityLevel.CRITICAL));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).isEqualTo(7);
    }

    @Test
    void nonMatchingSubscriptionNotInvoked() throws Exception {
        AlarmSubscriptionRegistry registry = new AlarmSubscriptionRegistry();
        CountDownLatch latch = new CountDownLatch(1);
        registry.register(new AlarmSubscription(AlarmSource.CES, null, null, a -> latch.countDown()));

        registry.publish(alarm(7L, AlarmSource.AOM, SeverityLevel.CRITICAL));

        assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isFalse();
    }

    private static Alarm alarm(long id, AlarmSource source, SeverityLevel severity) {
        LocalDateTime now = LocalDateTime.now();
        return new Alarm(id, source, "webhook", null, "fp", "res-1", "name", severity, AlarmStatus.FIRING,
                1, now, now, now, "svc", "prod", "{}", null);
    }
}
