package com.dpom.agent.core.script;

/**
 * 脚本类型。
 */
public enum ScriptType {
    /** 只读诊断脚本。 */
    READ_ONLY_DIAGNOSTIC,
    /** 修复脚本（仅生成，不执行）。 */
    MITIGATION
}
