package com.dpom.agent.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DPOMAgent 单实例 Java Web 应用入口。
 *
 * <p>研发侧症状驱动、假设驱动的故障调查 Agent；以 Spring MVC + Virtual Threads 运行。</p>
 */
@SpringBootApplication(scanBasePackages = "com.dpom.agent")
public class DpomAgentApplication {

    /**
     * 应用主方法。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DpomAgentApplication.class, args);
    }
}
