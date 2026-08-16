package com.dpom.agent.web;

import com.dpom.agent.common.codegraph.CodeGraphQueryException;
import com.dpom.agent.common.codegraph.CommitMismatchException;
import com.dpom.agent.common.codegraph.RegisteredRepository;
import com.dpom.agent.common.codegraph.SnapshotNotFoundException;
import com.dpom.agent.web.config.ConfigRepositoryRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 仓库注册表测试：确定映射、未知服务/commit mismatch fail closed、real-path containment 越界/symlink escape 拒绝。
 */
class ConfigRepositoryRegistryTest {

    @TempDir
    Path tmp;

    @Test
    void resolvesExactMatch() throws Exception {
        Path base = Files.createDirectories(tmp.resolve("snapshots"));
        Path repo = Files.createDirectories(base.resolve("asset-service"));
        ConfigRepositoryRegistry registry = new ConfigRepositoryRegistry(
                Map.of("asset-service", new ConfigRepositoryRegistry.Entry("1.0.0", "abc123", repo)), base);

        RegisteredRepository resolved = registry.resolve("asset-service", "abc123");

        assertThat(resolved.serviceCode()).isEqualTo("asset-service");
        assertThat(resolved.commitSha()).isEqualTo("abc123");
        assertThat(resolved.snapshotRoot()).isEqualTo(repo.toRealPath());
    }

    @Test
    void unknownServiceFailsClosed() {
        ConfigRepositoryRegistry registry = new ConfigRepositoryRegistry(Map.of(), null);

        assertThatThrownBy(() -> registry.resolve("unknown-service", "abc123"))
                .isInstanceOf(SnapshotNotFoundException.class);
    }

    @Test
    void commitMismatchFailsClosed() throws Exception {
        Path repo = Files.createDirectories(tmp.resolve("asset-service"));
        ConfigRepositoryRegistry registry = new ConfigRepositoryRegistry(
                Map.of("asset-service", new ConfigRepositoryRegistry.Entry("1.0.0", "abc123", repo)), null);

        assertThatThrownBy(() -> registry.resolve("asset-service", "deadbeef"))
                .isInstanceOf(CommitMismatchException.class);
    }

    @Test
    void pathOutsideBaseFailsClosed() throws Exception {
        Path base = Files.createDirectories(tmp.resolve("snapshots"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        ConfigRepositoryRegistry registry = new ConfigRepositoryRegistry(
                Map.of("asset-service", new ConfigRepositoryRegistry.Entry("1.0.0", "abc123", outside)), base);

        assertThatThrownBy(() -> registry.resolve("asset-service", "abc123"))
                .isInstanceOf(CodeGraphQueryException.class)
                .hasMessageContaining("越界");
    }

    @Test
    void symlinkEscapeFailsClosed() throws Exception {
        Path base = Files.createDirectories(tmp.resolve("snapshots"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Path link = base.resolve("asset-service");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            // 无符号链接权限时跳过（CI/Windows 非管理员）
            return;
        }
        ConfigRepositoryRegistry registry = new ConfigRepositoryRegistry(
                Map.of("asset-service", new ConfigRepositoryRegistry.Entry("1.0.0", "abc123", link)), base);

        assertThatThrownBy(() -> registry.resolve("asset-service", "abc123"))
                .isInstanceOf(CodeGraphQueryException.class);
    }

    @Test
    void resolveByProjectPathReturnsRegisteredRepo() throws Exception {
        Path repo = Files.createDirectories(tmp.resolve("asset-service"));
        ConfigRepositoryRegistry registry = new ConfigRepositoryRegistry(
                Map.of("asset-service", new ConfigRepositoryRegistry.Entry("1.0.0", "abc123", repo)), null);

        RegisteredRepository resolved = registry.resolveByProjectPath(repo.toRealPath().toString());

        assertThat(resolved.serviceCode()).isEqualTo("asset-service");
        assertThat(resolved.commitSha()).isEqualTo("abc123");
        assertThat(resolved.snapshotRoot()).isEqualTo(repo.toRealPath());
    }

    @Test
    void resolveByProjectPathRejectsUnregistered() throws Exception {
        Path repo = Files.createDirectories(tmp.resolve("asset-service"));
        Path other = Files.createDirectories(tmp.resolve("other-service"));
        ConfigRepositoryRegistry registry = new ConfigRepositoryRegistry(
                Map.of("asset-service", new ConfigRepositoryRegistry.Entry("1.0.0", "abc123", repo)), null);

        assertThatThrownBy(() -> registry.resolveByProjectPath(other.toRealPath().toString()))
                .isInstanceOf(SnapshotNotFoundException.class);
    }

    @Test
    void resolveByProjectPathRejectsOutsideBase() throws Exception {
        Path base = Files.createDirectories(tmp.resolve("snapshots"));
        Path repo = Files.createDirectories(base.resolve("asset-service"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        ConfigRepositoryRegistry registry = new ConfigRepositoryRegistry(
                Map.of("asset-service", new ConfigRepositoryRegistry.Entry("1.0.0", "abc123", repo)), base);

        assertThatThrownBy(() -> registry.resolveByProjectPath(outside.toRealPath().toString()))
                .isInstanceOf(CodeGraphQueryException.class)
                .hasMessageContaining("越界");
    }
}
