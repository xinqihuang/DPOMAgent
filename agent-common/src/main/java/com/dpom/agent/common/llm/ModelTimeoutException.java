package com.dpom.agent.common.llm;

/**
 * 模型调用超时异常。
 */
public class ModelTimeoutException extends ModelException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public ModelTimeoutException(String message) {
        super(message);
    }

    /**
     * 构造异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public ModelTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
