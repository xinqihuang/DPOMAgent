package com.dpom.agent.web.diagnosisevent;

/**
 * 不向调用方暴露失败细节的统一重放认证异常。
 */
public class ReplayAuthenticationException extends RuntimeException {

    /** 创建统一认证失败。 */
    public ReplayAuthenticationException() {
        super("REPLAY_AUTHENTICATION_FAILED");
    }
}
