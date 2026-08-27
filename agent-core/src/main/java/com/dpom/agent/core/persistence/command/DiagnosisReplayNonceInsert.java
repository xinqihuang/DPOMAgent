package com.dpom.agent.core.persistence.command;

import java.time.LocalDateTime;

/**
 * 持久化重放 nonce 插入命令。
 */
public class DiagnosisReplayNonceInsert {

    private Long id;
    private final String nonce;
    private final LocalDateTime expiresAt;

    /** 创建 nonce 命令。 */
    public DiagnosisReplayNonceInsert(String nonce, LocalDateTime expiresAt) {
        this.nonce = nonce;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNonce() { return nonce; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
