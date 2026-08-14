package com.dpom.agent.core.script;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 脚本策略校验器单元测试。
 */
class ScriptPolicyValidatorTest {

    private final ScriptPolicyValidator validator = new ScriptPolicyValidator();

    /**
     * 安全的只读 SQL 通过。
     */
    @Test
    void allowsReadOnlySql() {
        assertThatCode(() -> validator.validate(ScriptType.READ_ONLY_DIAGNOSTIC, "SELECT count(*) FROM asset"))
                .doesNotThrowAnyException();
    }

    /**
     * UPDATE 型只读脚本被拒绝。
     */
    @Test
    void rejectsUpdateAsReadOnly() {
        assertThatThrownBy(() -> validator.validate(ScriptType.READ_ONLY_DIAGNOSTIC, "UPDATE asset SET name='x'"))
                .isInstanceOf(ScriptPolicyViolation.class);
    }

    /**
     * rm/kill 等危险动作被拒绝。
     */
    @Test
    void rejectsDangerousShellCommands() {
        assertThatThrownBy(() -> validator.validate(ScriptType.READ_ONLY_DIAGNOSTIC, "rm -rf /tmp/x"))
                .isInstanceOf(ScriptPolicyViolation.class);
        assertThatThrownBy(() -> validator.validate(ScriptType.READ_ONLY_DIAGNOSTIC, "kill -9 123"))
                .isInstanceOf(ScriptPolicyViolation.class);
    }
}
