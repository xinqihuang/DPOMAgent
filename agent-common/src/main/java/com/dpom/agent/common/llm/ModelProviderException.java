package com.dpom.agent.common.llm;

/**
 * 模型 Provider 错误异常（非超时类错误）。
 */
public class ModelProviderException extends ModelException {

    /**
     * 构造异常。
     *
     * @param message 错误信息
     */
    public ModelProviderException(String message) {
        super(message);
    }

    /**
     * 构造异常。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public ModelProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
