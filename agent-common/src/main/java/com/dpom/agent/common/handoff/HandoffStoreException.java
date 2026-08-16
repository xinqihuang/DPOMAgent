package com.dpom.agent.common.handoff;

/**
 * 证据交接传输异常：携带稳定错误码，不携带凭据、路径、对象内容或请求详情。
 */
public class HandoffStoreException extends RuntimeException {

    private final String code;

    /**
     * 构造。
     *
     * @param code    稳定错误码
     * @param message 可读消息（不含敏感值）
     */
    public HandoffStoreException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 稳定错误码。
     *
     * @return 错误码
     */
    public String code() {
        return code;
    }
}
