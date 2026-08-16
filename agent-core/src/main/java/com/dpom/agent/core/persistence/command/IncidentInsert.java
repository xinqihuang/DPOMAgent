package com.dpom.agent.core.persistence.command;


/**
 * IncidentInsert 插入命令（mutable，自增主键回填 {@code id}）。
 */
public class IncidentInsert {

    private Long id;
    private final String serviceCode;
    private final String environment;
    private final String releaseVersion;
    private final String commitSha;
    private final String symptom;

    /**
     * 构造插入命令。
     */
    public IncidentInsert(String serviceCode, String environment, String releaseVersion, String commitSha, String symptom) {
        this.serviceCode = serviceCode;
        this.environment = environment;
        this.releaseVersion = releaseVersion;
        this.commitSha = commitSha;
        this.symptom = symptom;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getServiceCode() { return serviceCode; }
    public String getEnvironment() { return environment; }
    public String getReleaseVersion() { return releaseVersion; }
    public String getCommitSha() { return commitSha; }
    public String getSymptom() { return symptom; }
}
