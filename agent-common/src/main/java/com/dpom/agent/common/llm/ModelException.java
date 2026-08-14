package com.dpom.agent.common.llm;

/**
 * 模型调用异常基类。
 */
public class ModelException extends RuntimeException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public ModelException(String message) {
        super(message);
    }

    /**
     * 构造异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public ModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
