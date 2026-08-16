package com.dpom.agent.core.persistence.command;


/**
 * HandoffImportInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class HandoffImportInsert {

    private Long id;
    private final String packageId;
    private final String service;
    private final String release;
    private final String commit;

    /**
     * 构造插入命令。
     */
    public HandoffImportInsert(String packageId, String service, String release, String commit) {
        this.packageId = packageId;
        this.service = service;
        this.release = release;
        this.commit = commit;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPackageId() { return packageId; }
    public String getService() { return service; }
    public String getRelease() { return release; }
    public String getCommit() { return commit; }
}
