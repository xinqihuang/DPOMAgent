package com.dpom.agent.core.persistence.command;


/**
 * EvidenceBundleInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class EvidenceBundleInsert {

    private Long id;
    private final long investigationId;
    private final String serviceCode;
    private final String commitSha;
    private final String bundleJson;

    /**
     * 构造插入命令。
     */
    public EvidenceBundleInsert(long investigationId, String serviceCode, String commitSha, String bundleJson) {
        this.investigationId = investigationId;
        this.serviceCode = serviceCode;
        this.commitSha = commitSha;
        this.bundleJson = bundleJson;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getInvestigationId() { return investigationId; }
    public String getServiceCode() { return serviceCode; }
    public String getCommitSha() { return commitSha; }
    public String getBundleJson() { return bundleJson; }
}
