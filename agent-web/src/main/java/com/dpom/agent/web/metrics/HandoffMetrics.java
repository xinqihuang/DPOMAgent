package com.dpom.agent.web.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 证据交接低基数指标：复用既有标签白名单（resultType/errorCode），best-effort。
 */
@Component
public class HandoffMetrics {

    private final MeterRegistry registry;

    /**
     * 构造器注入。
     *
     * @param registry 指标注册表
     */
    public HandoffMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录升级判定（eligible -> INSUFFICIENT_EVIDENCE，否则 NONE）。
     *
     * @param eligible 是否满足升级条件
     */
    public void recordEscalation(boolean eligible) {
        Counter.builder("dpom.handoff.escalation")
                .tag("resultType", eligible ? "INSUFFICIENT_EVIDENCE" : "NONE")
                .register(registry).increment();
    }

    /**
     * 记录上传结果（成功 -> NONE，否则 ERROR）。
     *
     * @param success 是否成功
     */
    public void recordUpload(boolean success) {
        Counter.builder("dpom.handoff.upload")
                .tag("errorCode", success ? "NONE" : "ERROR")
                .register(registry).increment();
    }

    /**
     * 记录研发侧导入次数（无标签）。
     */
    public void recordImport() {
        Counter.builder("dpom.handoff.import").register(registry).increment();
    }
}
