package com.dpom.agent.core.persistence.command;


/**
 * HandoffUploadInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class HandoffUploadInsert {

    private Long id;
    private final long investigationId;
    private final String packageId;
    private final int schemaVersion;
    private final String checksum;
    private final long sizeBytes;

    /**
     * 构造插入命令。
     */
    public HandoffUploadInsert(long investigationId, String packageId, int schemaVersion, String checksum, long sizeBytes) {
        this.investigationId = investigationId;
        this.packageId = packageId;
        this.schemaVersion = schemaVersion;
        this.checksum = checksum;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getInvestigationId() { return investigationId; }
    public String getPackageId() { return packageId; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getChecksum() { return checksum; }
    public long getSizeBytes() { return sizeBytes; }
}
