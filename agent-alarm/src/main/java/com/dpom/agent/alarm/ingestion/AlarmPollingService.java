package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.common.alarm.AlarmSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 告警轮询调度：定时从 DPOMBaseMCPServer 只读网关增量拉取告警，与 webhook 共用接入管道。
 *
 * <p>游标在内存维护（MVP）；轮询默认关闭，由 {@code dpom.alarm.poll.enabled} 开启。
 * 仅在装配了 {@link AlarmSourceGateway} 时启用（网关由外部适配器提供）。
 * 单实例 Spring MVC + 虚拟线程，不引入消息中间件。</p>
 */
@Service
@ConditionalOnBean(AlarmSourceGateway.class)
public class AlarmPollingService {

    private static final Logger LOG = LoggerFactory.getLogger(AlarmPollingService.class);

    private final AlarmSourceGateway gateway;
    private final AlarmIngestionService ingestionService;
    private final int limit;
    private final boolean enabled;
    private final Map<AlarmSource, LocalDateTime> cursors = new EnumMap<>(AlarmSource.class);

    /**
     * 构造轮询服务。
     *
     * @param gateway          来源只读网关
     * @param ingestionService 接入服务
     * @param limit            单次拉取上限
     * @param enabled          是否启用轮询
     */
    public AlarmPollingService(AlarmSourceGateway gateway, AlarmIngestionService ingestionService,
            @Value("${dpom.alarm.poll.limit:100}") int limit,
            @Value("${dpom.alarm.poll.enabled:false}") boolean enabled) {
        this.gateway = gateway;
        this.ingestionService = ingestionService;
        this.limit = limit;
        this.enabled = enabled;
    }

    /**
     * 定时轮询所有来源。
     */
    @Scheduled(fixedDelayString = "${dpom.alarm.poll.fixed-delay-ms:300000}")
    public void pollAll() {
        if (!enabled) {
            return;
        }
        for (AlarmSource source : AlarmSource.values()) {
            try {
                pollOnce(source);
            } catch (RuntimeException e) {
                LOG.error("告警轮询失败 source={}", source, e);
            }
        }
    }

    /**
     * 单次轮询指定来源：增量拉取并接入，推进游标。
     *
     * @param source 来源服务
     * @return 本轮接入的事件数
     */
    int pollOnce(AlarmSource source) {
        List<RawAlarmEvent> events = gateway.fetchSince(source, cursors.get(source), limit);
        int count = 0;
        LocalDateTime maxOccurred = cursors.get(source);
        for (RawAlarmEvent event : events) {
            ingestionService.ingest(source, event.rawPayload(), "poll");
            count++;
            if (maxOccurred == null || (event.occurredAt() != null && event.occurredAt().isAfter(maxOccurred))) {
                maxOccurred = event.occurredAt();
            }
        }
        if (maxOccurred != null) {
            cursors.put(source, maxOccurred);
        }
        if (count > 0) {
            LOG.info("告警轮询接入 source={} count={}", source, count);
        }
        return count;
    }
}
