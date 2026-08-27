package com.dpom.agent.core.authority;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 权威域不得依赖框架或适配器。 */
class AuthorityDomainArchitectureTest {

    @Test
    void authorityPackageUsesOnlyJdkAndExistingDomainEnums() throws IOException {
        Path directory = Path.of("src/main/java/com/dpom/agent/core/authority");
        assertThat(directory).isDirectory();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file))
                        .as(file.toString())
                        .doesNotContain("org.springframework")
                        .doesNotContain("org.apache.ibatis")
                        .doesNotContain("com.fasterxml.jackson")
                        .doesNotContain("jakarta.")
                        .doesNotContain("com.huawei")
                        .doesNotContain("com.dpom.agent.web");
            }
        }
    }
}
