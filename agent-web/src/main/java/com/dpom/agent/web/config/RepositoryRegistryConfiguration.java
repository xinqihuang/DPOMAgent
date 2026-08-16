package com.dpom.agent.web.config;

import com.dpom.agent.common.codegraph.RepositoryRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 仓库注册表装配：仅 development profile（dpom.codegraph.enabled=true）装配，production 无源码访问。
 */
@Configuration
@ConditionalOnProperty(name = "dpom.codegraph.enabled", havingValue = "true")
@EnableConfigurationProperties(RepositoryRegistryProperties.class)
public class RepositoryRegistryConfiguration {

    /**
     * 从配置构建仓库注册表。
     *
     * @param props dpom.repositories 配置
     * @return 仓库注册表
     */
    @Bean
    public RepositoryRegistry repositoryRegistry(RepositoryRegistryProperties props) {
        Map<String, ConfigRepositoryRegistry.Entry> entries = new LinkedHashMap<>();
        for (Map.Entry<String, RepositoryRegistryProperties.Repo> e : props.getServices().entrySet()) {
            RepositoryRegistryProperties.Repo repo = e.getValue();
            entries.put(e.getKey(), new ConfigRepositoryRegistry.Entry(repo.getRelease(), repo.getCommit(),
                    Path.of(repo.getPath())));
        }
        Path allowedBase = props.getBasePath() == null || props.getBasePath().isBlank()
                ? null : Path.of(props.getBasePath());
        return new ConfigRepositoryRegistry(entries, allowedBase);
    }
}
