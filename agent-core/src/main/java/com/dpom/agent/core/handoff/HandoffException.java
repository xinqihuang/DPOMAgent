package com.dpom.agent.core.handoff;

/**
 * 证据交接业务异常：携带稳定错误码，不携带源码、凭据、路径或请求内容。
 */
public class HandoffException extends RuntimeException {

    private final HandoffErrorCode code;

    /**
     * 构造。
     *
     * @param code    稳定错误码
     * @param message 可读消息（不含敏感值）
     */
    public HandoffException(HandoffErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 稳定错误码。
     *
     * @return 错误码
     */
    public HandoffErrorCode code() {
        return code;
    }
}
