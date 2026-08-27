package com.dpom.agent.web.authorityapi;

/** Authority 只读 API 认证失败。 */
public class AuthorityAuthenticationException extends RuntimeException {

    /** 创建不暴露失败细节的异常。 */
    public AuthorityAuthenticationException() {
        super("AUTHORITY_AUTHENTICATION_FAILED");
    }
}

