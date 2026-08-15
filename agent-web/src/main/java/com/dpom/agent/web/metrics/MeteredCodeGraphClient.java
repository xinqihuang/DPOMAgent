package com.dpom.agent.web.metrics;

import com.dpom.agent.common.codegraph.CallStep;
import com.dpom.agent.common.codegraph.ClassHierarchy;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.web.health.AdapterHealthRegistry;

import java.util.List;

/**
 * 计量装饰器：CodeGraphClient（CodeGraphContext）。
 */
public class MeteredCodeGraphClient implements CodeGraphClient {

    private final CodeGraphClient delegate;
    private final AdapterMetrics metrics;

    public MeteredCodeGraphClient(CodeGraphClient delegate, AdapterMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override public CodeSnapshot resolveSnapshot(String serviceCode, String commitSha) {
        return metrics.record("codegraph", AdapterHealthRegistry.Adapter.CODEGRAPH,
                () -> delegate.resolveSnapshot(serviceCode, commitSha));
    }

    @Override public CodeSnapshot getSnapshot(String snapshotId) {
        return metrics.record("codegraph", AdapterHealthRegistry.Adapter.CODEGRAPH,
                () -> delegate.getSnapshot(snapshotId));
    }

    @Override public List<Symbol> findSymbol(String snapshotId, String name) {
        return metrics.record("codegraph", AdapterHealthRegistry.Adapter.CODEGRAPH,
                () -> delegate.findSymbol(snapshotId, name));
    }

    @Override public List<Symbol> findCallers(String snapshotId, String symbol) {
        return metrics.record("codegraph", AdapterHealthRegistry.Adapter.CODEGRAPH,
                () -> delegate.findCallers(snapshotId, symbol));
    }

    @Override public List<Symbol> findCallees(String snapshotId, String symbol) {
        return metrics.record("codegraph", AdapterHealthRegistry.Adapter.CODEGRAPH,
                () -> delegate.findCallees(snapshotId, symbol));
    }

    @Override public List<CallStep> findCallChain(String snapshotId, String fromSymbol, String toSymbol) {
        return metrics.record("codegraph", AdapterHealthRegistry.Adapter.CODEGRAPH,
                () -> delegate.findCallChain(snapshotId, fromSymbol, toSymbol));
    }

    @Override public ClassHierarchy findClassHierarchy(String snapshotId, String className) {
        return metrics.record("codegraph", AdapterHealthRegistry.Adapter.CODEGRAPH,
                () -> delegate.findClassHierarchy(snapshotId, className));
    }
}
