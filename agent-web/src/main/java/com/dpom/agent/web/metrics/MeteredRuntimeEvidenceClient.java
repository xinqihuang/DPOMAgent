package com.dpom.agent.web.metrics;

import com.dpom.agent.common.runtime.ObservationInput;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;

import java.util.List;

/**
 * 计量装饰器：RuntimeEvidenceClient（DPOMBaseMCPServer，仅计量、不参与被动健康观测）。
 */
public class MeteredRuntimeEvidenceClient implements RuntimeEvidenceClient {

    private final RuntimeEvidenceClient delegate;
    private final AdapterMetrics metrics;

    public MeteredRuntimeEvidenceClient(RuntimeEvidenceClient delegate, AdapterMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override public List<ObservationInput> searchLogs(String serviceCode, String environment, String keyword,
            String timeRange) {
        return metrics.record("runtime", null, () -> delegate.searchLogs(serviceCode, environment, keyword, timeRange));
    }

    @Override public List<ObservationInput> queryTrace(String traceId) {
        return metrics.record("runtime", null, () -> delegate.queryTrace(traceId));
    }

    @Override public List<ObservationInput> queryAlerts(String serviceCode, String environment, String timeRange) {
        return metrics.record("runtime", null, () -> delegate.queryAlerts(serviceCode, environment, timeRange));
    }

    @Override public List<ObservationInput> queryMetrics(String serviceCode, String metricName, String timeRange) {
        return metrics.record("runtime", null, () -> delegate.queryMetrics(serviceCode, metricName, timeRange));
    }
}
