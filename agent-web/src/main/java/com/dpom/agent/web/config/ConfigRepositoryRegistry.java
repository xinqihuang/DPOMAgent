package com.dpom.agent.web.config;

import com.dpom.agent.common.codegraph.CodeGraphQueryException;
import com.dpom.agent.common.codegraph.CommitMismatchException;
import com.dpom.agent.common.codegraph.RegisteredRepository;
import com.dpom.agent.common.codegraph.RepositoryRegistry;
import com.dpom.agent.common.codegraph.SnapshotNotFoundException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * 配置驱动的仓库注册表：serviceCode + release/commit → 快照根目录（projectPath 来源）。
 *
 * <p>未知服务、commit 不一致、路径不可解析、real-path 越界或 symlink escape 一律 fail closed，
 * 绝无「找不到就选第一个仓库」。</p>
 */
public class ConfigRepositoryRegistry implements RepositoryRegistry {

    /** 单个已注册仓库的配置条目。 */
    public record Entry(String release, String commit, Path path) {
    }

    private final Map<String, Entry> entries;
    private final Path allowedBase;

    /**
     * 构造注册表。
     *
     * @param entries     serviceCode → 仓库配置
     * @param allowedBase 允许的快照根目录基路径（可为空表示不做基路径约束）
     */
    public ConfigRepositoryRegistry(Map<String, Entry> entries, Path allowedBase) {
        this.entries = Map.copyOf(entries);
        this.allowedBase = allowedBase;
    }

    @Override
    public RegisteredRepository resolve(String serviceCode, String commitSha) {
        Entry entry = entries.get(serviceCode);
        if (entry == null) {
            throw new SnapshotNotFoundException("未注册服务：" + serviceCode);
        }
        if (!entry.commit().equals(commitSha)) {
            throw new CommitMismatchException("commit 不匹配：期望 " + entry.commit() + "，实际 " + commitSha);
        }
        Path real = toRealPath(entry.path(), "快照根目录不可解析：" + entry.path());
        requireContained(real);
        return new RegisteredRepository(serviceCode, entry.release(), commitSha, real);
    }

    @Override
    public RegisteredRepository resolveByProjectPath(String projectPath) {
        Path candidate = toRealPath(Path.of(projectPath), "projectPath 不可解析：" + projectPath);
        requireContained(candidate);
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            Path root = toRealPath(e.getValue().path(), "快照根目录不可解析：" + e.getValue().path());
            if (root.equals(candidate)) {
                return new RegisteredRepository(e.getKey(), e.getValue().release(), e.getValue().commit(), root);
            }
        }
        throw new SnapshotNotFoundException("未注册的 projectPath：" + projectPath);
    }

    /**
     * real-path containment：real 必须在允许基路径之内。
     *
     * @param real 已解析的 real path
     */
    private void requireContained(Path real) {
        if (allowedBase == null) {
            return;
        }
        Path realBase = toRealPath(allowedBase, "允许基路径不可解析：" + allowedBase);
        if (!real.startsWith(realBase)) {
            throw new CodeGraphQueryException("projectPath 越界：" + real);
        }
    }

    private static Path toRealPath(Path path, String message) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            throw new SnapshotNotFoundException(message);
        }
    }
}
