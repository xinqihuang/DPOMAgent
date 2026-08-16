package com.dpom.agent.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * dpom.repositories 配置：serviceCode → release/commit/path 映射与允许基路径。
 */
@ConfigurationProperties(prefix = "dpom.repositories")
public class RepositoryRegistryProperties {

    private Map<String, Repo> services = new HashMap<>();
    private String basePath = "";

    /** 单个仓库配置。 */
    public static class Repo {
        private String release = "";
        private String commit = "";
        private String path = "";

        public String getRelease() {
            return release;
        }

        public void setRelease(String release) {
            this.release = release;
        }

        public String getCommit() {
            return commit;
        }

        public void setCommit(String commit) {
            this.commit = commit;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public Map<String, Repo> getServices() {
        return services;
    }

    public void setServices(Map<String, Repo> services) {
        this.services = services;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
}
