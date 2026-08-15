package com.dpom.agent.web.metrics;

import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.llm.ModelTurnRequest;
import com.dpom.agent.common.llm.ModelTurnResult;
import com.dpom.agent.web.health.AdapterHealthRegistry;

/**
 * 计量装饰器：ModelClient（DeepSeek）。
 */
public class MeteredModelClient implements ModelClient {

    private final ModelClient delegate;
    private final AdapterMetrics metrics;

    public MeteredModelClient(ModelClient delegate, AdapterMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public ModelTurnResult complete(ModelTurnRequest request) {
        return metrics.record("llm", AdapterHealthRegistry.Adapter.LLM, () -> delegate.complete(request));
    }
}
