package com.dpom.agent.web.authorityapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 对只读 Authority API 执行常量时间 Bearer token 校验。 */
@Component
public class AuthorityReadAuthenticator {

    private final byte[] expectedDigest;
    private final boolean configured;

    /** 从外部配置读取 token；空配置保持 fail-closed。 */
    public AuthorityReadAuthenticator(@Value("${dpom.authority.read-token:}") String token,
            @Value("${dpom.authority.api.enabled:false}") boolean enabled) {
        configured = enabled && token != null && token.length() >= 32;
        expectedDigest = digest(configured ? token : "UNCONFIGURED_AUTHORITY_TOKEN");
    }

    /** 校验 Authorization: Bearer 请求头。 */
    public void authenticate(String authorization) {
        String supplied = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : "";
        if (!configured || !MessageDigest.isEqual(expectedDigest, digest(supplied))) {
            throw new AuthorityAuthenticationException();
        }
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", e);
        }
    }
}
