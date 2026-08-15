package com.dpom.agent.core.eval;

import com.dpom.agent.common.codegraph.CallStep;
import com.dpom.agent.common.codegraph.ClassHierarchy;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.codegraph.Symbol;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 录制代码图客户端：只读 recorded-codegraph.json，不接触 expected.json。
 */
public class RecordedCodeGraphClient implements CodeGraphClient {

    private final List<Symbol> symbols;

    /**
     * 构造。
     *
     * @param recordedFile recorded-codegraph.json 路径
     * @throws IOException 读取失败
     */
    public RecordedCodeGraphClient(Path recordedFile) throws IOException {
        symbols = new ObjectMapper().readValue(recordedFile.toFile(), new TypeReference<List<Symbol>>() {
        });
    }

    @Override
    public CodeSnapshot resolveSnapshot(String serviceCode, String commitSha) {
        return new CodeSnapshot("s1", serviceCode, commitSha, "/repos/x", SnapshotStatus.READY);
    }

    @Override
    public CodeSnapshot getSnapshot(String snapshotId) {
        return new CodeSnapshot(snapshotId, null, null, snapshotId, SnapshotStatus.READY);
    }

    @Override
    public List<Symbol> findSymbol(String snapshotId, String name) {
        return symbols;
    }

    @Override
    public List<Symbol> findCallers(String snapshotId, String symbol) {
        return List.of();
    }

    @Override
    public List<Symbol> findCallees(String snapshotId, String symbol) {
        return List.of();
    }

    @Override
    public List<CallStep> findCallChain(String snapshotId, String fromSymbol, String toSymbol) {
        return List.of();
    }

    @Override
    public ClassHierarchy findClassHierarchy(String snapshotId, String className) {
        return new ClassHierarchy(className, List.of());
    }
}
