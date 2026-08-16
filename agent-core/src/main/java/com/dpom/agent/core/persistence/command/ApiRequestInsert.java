package com.dpom.agent.core.persistence.command;


/**
 * ApiRequestInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class ApiRequestInsert {

    private Long id;
    private final String idempotencyKey;
    private final String payloadHash;
    private final long investigationId;
    private final String status;

    /**
     * 构造插入命令。
     */
    public ApiRequestInsert(String idempotencyKey, String payloadHash, long investigationId, String status) {
        this.idempotencyKey = idempotencyKey;
        this.payloadHash = payloadHash;
        this.investigationId = investigationId;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPayloadHash() { return payloadHash; }
    public long getInvestigationId() { return investigationId; }
    public String getStatus() { return status; }
}
