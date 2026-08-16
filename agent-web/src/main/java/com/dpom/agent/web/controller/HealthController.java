package com.dpom.agent.web.controller;

import com.dpom.agent.core.persistence.HealthCheckMapper;
import com.dpom.agent.web.health.AdapterHealthRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康/就绪信息：整体状态由 DB 与有界执行器容量推导；外部能力为被动观测 UP/DOWN/UNKNOWN。
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final HealthCheckMapper healthCheckMapper;
    private final ThreadPoolTaskExecutor investigationExecutor;
    private final AdapterHealthRegistry adapterHealth;

    public HealthController(HealthCheckMapper healthCheckMapper,
                            @Qualifier("investigationExecutor") ThreadPoolTaskExecutor investigationExecutor,
                            AdapterHealthRegistry adapterHealth) {
        this.healthCheckMapper = healthCheckMapper;
        this.investigationExecutor = investigationExecutor;
        this.adapterHealth = adapterHealth;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        String db = dbStatus();
        boolean capacity = executorAvailable();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP".equals(db) && capacity ? "UP" : "DOWN");
        body.put("db", db);
        body.put("executor", executorInfo(capacity));
        Map<String, String> external = new LinkedHashMap<>();
        external.put("drain3", state(AdapterHealthRegistry.Adapter.DRAIN3));
        external.put("codegraph", state(AdapterHealthRegistry.Adapter.CODEGRAPH));
        external.put("llm", state(AdapterHealthRegistry.Adapter.LLM));
        body.put("external", external);
        return body;
    }

    private String state(AdapterHealthRegistry.Adapter adapter) {
        return adapterHealth.state(adapter).name();
    }

    private Map<String, Object> executorInfo(boolean available) {
        int queueSize = investigationExecutor.getThreadPoolExecutor().getQueue().size();
        int queueCapacity = queueSize + investigationExecutor.getThreadPoolExecutor().getQueue().remainingCapacity();
        Map<String, Object> executor = new LinkedHashMap<>();
        executor.put("active", investigationExecutor.getActiveCount());
        executor.put("poolSize", investigationExecutor.getPoolSize());
        executor.put("queueSize", queueSize);
        executor.put("queueCapacity", queueCapacity);
        executor.put("available", available);
        return executor;
    }

    private boolean executorAvailable() {
        return investigationExecutor.getActiveCount() < investigationExecutor.getMaxPoolSize()
                || investigationExecutor.getThreadPoolExecutor().getQueue().remainingCapacity() > 0;
    }

    private String dbStatus() {
        try {
            healthCheckMapper.ping();
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
