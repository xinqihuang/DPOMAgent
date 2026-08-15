package com.dpom.agent.web.metrics;

import com.dpom.agent.common.logtemplate.LogParseResult;
import com.dpom.agent.common.logtemplate.LogTemplate;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.web.health.AdapterHealthRegistry;

import java.util.List;

/**
 * 计量装饰器：LogTemplateMinerClient（Drain3）。
 */
public class MeteredLogTemplateMinerClient implements LogTemplateMinerClient {

    private final LogTemplateMinerClient delegate;
    private final AdapterMetrics metrics;

    public MeteredLogTemplateMinerClient(LogTemplateMinerClient delegate, AdapterMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override public List<LogParseResult> parseLogs(List<String> lines) {
        return metrics.record("drain3", AdapterHealthRegistry.Adapter.DRAIN3, () -> delegate.parseLogs(lines));
    }

    @Override public List<LogTemplate> listTemplates() {
        return metrics.record("drain3", AdapterHealthRegistry.Adapter.DRAIN3, () -> delegate.listTemplates());
    }
}
