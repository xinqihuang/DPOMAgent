package com.dpom.agent.core.script;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 脚本策略校验器：保守地拒绝只读诊断脚本中的修改动作。
 *
 * <p>本校验为静态启发式，不声称能绝对防止所有副作用，仅拦截常见危险模式。</p>
 */
@Component
public class ScriptPolicyValidator {

    private static final List<String> FORBIDDEN_PATTERNS = List.of(
            "rm ", "kill", "restart", "systemctl", "kubectl delete", "shutdown", "reboot",
            "UPDATE", "DELETE", "INSERT", "DROP", "ALTER", "CREATE", "TRUNCATE", "MERGE");

    /**
     * 校验脚本内容。
     *
     * @param type    脚本类型
     * @param content 脚本内容
     * @throws ScriptPolicyViolation 校验失败
     */
    public void validate(ScriptType type, String content) {
        if (type != ScriptType.READ_ONLY_DIAGNOSTIC) {
            return;
        }
        if (content == null) {
            return;
        }
        String upper = content.toUpperCase();
        for (String pattern : FORBIDDEN_PATTERNS) {
            if (upper.contains(pattern.toUpperCase())) {
                throw new ScriptPolicyViolation("只读诊断脚本包含禁止动作：" + pattern.trim());
            }
        }
    }
}
