package com.dpom.agent.core.diagnosisevent;

/**
 * JSON 无法安全规范化时抛出的异常。
 */
public class CanonicalJsonException extends RuntimeException {

    /**
     * 创建规范化异常。
     *
     * @param cause 原始异常
     */
    public CanonicalJsonException(Throwable cause) {
        super("CANONICAL_JSON_FAILED", cause);
    }
}
