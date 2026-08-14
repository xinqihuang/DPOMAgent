package com.dpom.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 应用上下文冒烟测试：验证 Spring MVC 骨架可启动。
 */
@SpringBootTest
class DpomAgentApplicationTests {

    /**
     * 验证应用上下文能够加载。
     */
    @Test
    void contextLoads() {
        // 上下文成功加载即视为通过
    }
}
