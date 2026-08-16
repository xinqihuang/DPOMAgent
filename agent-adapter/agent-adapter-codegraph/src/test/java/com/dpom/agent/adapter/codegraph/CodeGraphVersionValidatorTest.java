package com.dpom.agent.adapter.codegraph;

import com.dpom.agent.common.codegraph.CodeGraphQueryException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 版本校验测试：可执行文件不存在、版本不匹配 fail，版本匹配通过。
 */
class CodeGraphVersionValidatorTest {

    private final CodeGraphVersionValidator validator = new CodeGraphVersionValidator();

    @Test
    void missingExecutableFails() {
        Path missing = Path.of("D:\\nonexistent\\codegraph.exe");
        assertThatThrownBy(() -> validator.validate(missing, "1.5.0"))
                .isInstanceOf(CodeGraphQueryException.class)
                .hasMessageContaining("可执行文件不存在");
    }

    @Test
    void versionMismatchFails() {
        assertThatThrownBy(() -> CodeGraphVersionValidator.requireVersionMatches("0.9.0", "1.5.0",
                Path.of("codegraph.exe")))
                .isInstanceOf(CodeGraphQueryException.class)
                .hasMessageContaining("版本不匹配");
    }

    @Test
    void versionMatchPasses() {
        assertThatCode(() -> CodeGraphVersionValidator.requireVersionMatches("1.5.0", "1.5.0",
                Path.of("codegraph.exe"))).doesNotThrowAnyException();
    }
}
