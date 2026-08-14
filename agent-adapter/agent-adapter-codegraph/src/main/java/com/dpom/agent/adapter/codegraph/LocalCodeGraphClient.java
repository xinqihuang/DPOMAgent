package com.dpom.agent.adapter.codegraph;

import com.dpom.agent.common.codegraph.CallStep;
import com.dpom.agent.common.codegraph.ClassHierarchy;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.common.codegraph.Symbol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 本地工作区代码图：在指定源码目录内做文本级符号搜索。
 *
 * <p>当 CodeGraphContext 的图数据库后端不可用时，作为降级实现（真实源码搜索，非图查询）。</p>
 */
public class LocalCodeGraphClient implements CodeGraphClient {

    private final Path root;

    /**
     * 构造客户端。
     *
     * @param root 源码根目录
     */
    public LocalCodeGraphClient(Path root) {
        this.root = root;
    }

    @Override
    public CodeSnapshot resolveSnapshot(String serviceCode, String commitSha) {
        return new CodeSnapshot(root.toString(), serviceCode, commitSha, root.toString(), SnapshotStatus.READY);
    }

    @Override
    public CodeSnapshot getSnapshot(String snapshotId) {
        return new CodeSnapshot(snapshotId, null, null, snapshotId, SnapshotStatus.READY);
    }

    @Override
    public List<Symbol> findSymbol(String snapshotId, String name) {
        return search(name, "symbol");
    }

    @Override
    public List<Symbol> findCallers(String snapshotId, String symbol) {
        return search(symbol, "caller");
    }

    @Override
    public List<Symbol> findCallees(String snapshotId, String symbol) {
        return search(symbol, "callee");
    }

    @Override
    public List<CallStep> findCallChain(String snapshotId, String fromSymbol, String toSymbol) {
        return List.of();
    }

    @Override
    public ClassHierarchy findClassHierarchy(String snapshotId, String className) {
        return new ClassHierarchy(className, List.of());
    }

    /**
     * 在源码内按符号短名做文本搜索。
     */
    private List<Symbol> search(String name, String kind) {
        List<Symbol> result = new ArrayList<>();
        String shortName = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(file -> searchFile(file, shortName, name, kind, result));
        } catch (IOException ignored) {
            // 忽略扫描异常
        }
        return result;
    }

    /**
     * 在单个文件内搜索符号。
     */
    private void searchFile(Path file, String shortName, String name, String kind, List<Symbol> result) {
        if (result.size() >= 50) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size() && result.size() < 50; i++) {
                if (lines.get(i).contains(shortName)) {
                    result.add(new Symbol(name, kind, root.relativize(file).toString().replace('\\', '/'), i + 1));
                }
            }
        } catch (IOException ignored) {
            // 忽略单文件读取异常
        }
    }
}
