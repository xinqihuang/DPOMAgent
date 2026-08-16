package com.dpom.agent.adapter.codegraph;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeGraphQueryException;
import com.dpom.agent.common.codegraph.RegisteredRepository;
import com.dpom.agent.common.codegraph.RepositoryRegistry;
import com.dpom.agent.common.codegraph.SnapshotNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CodeGraph 装配边界测试：
 * - production 缺省装配 fail-closed 的禁用态 port，无 stdio 子进程参数、无版本校验器；
 * - development 在创建 stdio 客户端前校验 executable 存在与版本匹配，缺失/不匹配 fail closed。
 */
class CodeGraphAdapterConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CodeGraphAdapterConfiguration.class, StubRegistryConfig.class);

    @Test
    void productionDefaultsToDisabledPort() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(CodeGraphClient.class);
            assertThat(ctx.getBean(CodeGraphClient.class)).isInstanceOf(DisabledCodeGraphClient.class);
            assertThat(ctx).doesNotHaveBean(CodeGraphProcessParameters.class);
            assertThat(ctx).doesNotHaveBean(CodeGraphVersionValidator.class);
        });
    }

    @Test
    void developmentAssemblesStdioWhenVersionMatches() {
        runner.withUserConfiguration(NoopValidatorConfig.class)
                .withPropertyValues("dpom.codegraph.enabled=true", "dpom.codegraph.executable-path=/fake/codegraph")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(CodeGraphClient.class);
                    assertThat(ctx.getBean(CodeGraphClient.class)).isInstanceOf(CodeGraphStdioClient.class);
                    assertThat(ctx).hasSingleBean(CodeGraphProcessParameters.class);
                });
    }

    @Test
    void executableMissingFailsAssembly() {
        runner.withPropertyValues("dpom.codegraph.enabled=true",
                        "dpom.codegraph.executable-path=D:\\nonexistent\\codegraph.exe")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).hasMessageContaining("可执行文件不存在");
                });
    }

    @Test
    void versionMismatchFailsAssembly() {
        runner.withUserConfiguration(MismatchValidatorConfig.class)
                .withPropertyValues("dpom.codegraph.enabled=true", "dpom.codegraph.executable-path=/fake/codegraph")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).hasMessageContaining("版本不匹配");
                });
    }

    @Configuration
    static class StubRegistryConfig {

        @Bean
        RepositoryRegistry repositoryRegistry() {
            return new RepositoryRegistry() {
                @Override
                public RegisteredRepository resolve(String serviceCode, String commitSha) {
                    return new RegisteredRepository(serviceCode, null, commitSha, Path.of("/snapshots/x"));
                }

                @Override
                public RegisteredRepository resolveByProjectPath(String projectPath) {
                    if (!Path.of(projectPath).equals(Path.of("/snapshots/x"))) {
                        throw new SnapshotNotFoundException("未注册的 projectPath：" + projectPath);
                    }
                    return new RegisteredRepository("x", null, "c", Path.of("/snapshots/x"));
                }
            };
        }
    }

    @Configuration
    static class NoopValidatorConfig {

        @Bean
        @Primary
        CodeGraphVersionValidator noopVersionValidator() {
            return new CodeGraphVersionValidator() {
                @Override
                public void validate(Path executable, String expectedVersion) {
                    // 版本匹配：不抛异常
                }
            };
        }
    }

    @Configuration
    static class MismatchValidatorConfig {

        @Bean
        @Primary
        CodeGraphVersionValidator mismatchVersionValidator() {
            return new CodeGraphVersionValidator() {
                @Override
                public void validate(Path executable, String expectedVersion) {
                    throw new CodeGraphQueryException("CodeGraph 版本不匹配：期望 " + expectedVersion + "，实际 0.9.0");
                }
            };
        }
    }
}
