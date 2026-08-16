package com.dpom.agent.adapter.codegraph;

import com.dpom.agent.common.codegraph.CallStep;
import com.dpom.agent.common.codegraph.ClassHierarchy;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeGraphQueryException;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotNotFoundException;
import com.dpom.agent.common.codegraph.Symbol;

import java.util.List;

/**
 * 禁用态代码图客户端：production profile 装配，不启动 stdio 子进程、不访问源码，调用即 fail closed。
 */
public class DisabledCodeGraphClient implements CodeGraphClient {

    private static final String MESSAGE = "CodeGraph 未启用（production profile 无源码访问）";

    @Override
    public CodeSnapshot resolveSnapshot(String serviceCode, String commitSha) {
        throw new SnapshotNotFoundException(MESSAGE);
    }

    @Override
    public CodeSnapshot getSnapshot(String snapshotId) {
        throw new SnapshotNotFoundException(MESSAGE);
    }

    @Override
    public List<Symbol> findSymbol(String snapshotId, String name) {
        throw new CodeGraphQueryException(MESSAGE);
    }

    @Override
    public List<Symbol> findCallers(String snapshotId, String symbol) {
        throw new CodeGraphQueryException(MESSAGE);
    }

    @Override
    public List<Symbol> findCallees(String snapshotId, String symbol) {
        throw new CodeGraphQueryException(MESSAGE);
    }

    @Override
    public List<CallStep> findCallChain(String snapshotId, String fromSymbol, String toSymbol) {
        throw new CodeGraphQueryException(MESSAGE);
    }

    @Override
    public ClassHierarchy findClassHierarchy(String snapshotId, String className) {
        throw new CodeGraphQueryException(MESSAGE);
    }
}
