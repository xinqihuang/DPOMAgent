package com.dpom.agent.alarm.correlation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 拓扑源装配：默认提供静态配置拓扑源，可被自定义 {@link TopologySource} bean 覆盖。
 */
@Configuration
public class TopologyConfig {

    /**
     * 默认静态拓扑源（空邻接，仅同资源可关联）。
     *
     * @return 静态拓扑源
     */
    @Bean
    @ConditionalOnMissingBean(TopologySource.class)
    public TopologySource staticTopologySource() {
        return new StaticTopologySource();
    }
}
