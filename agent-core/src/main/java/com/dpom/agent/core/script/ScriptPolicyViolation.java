package com.dpom.agent.core.script;

/**
 * 脚本策略校验失败异常。
 */
public class ScriptPolicyViolation extends RuntimeException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public ScriptPolicyViolation(String message) {
        super(message);
    }
}
