package com.dpom.agent.adapter.codegraph;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.RepositoryRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * CodeGraph 适配器装配：development（dpom.codegraph.enabled=true）在创建 stdio 客户端前校验 executable 与版本，
 * 校验失败 fail closed；production 缺省装配 fail-closed 的禁用态 port（无 stdio 子进程、无源码访问）。
 */
@Configuration
public class CodeGraphAdapterConfiguration {

    /**
     * 受控进程参数（executable 来自服务端配置，参数与环境由代码固定）。
     *
     * @param executablePath CodeGraph 可执行文件路径
     * @param mcpTools       显式 MCP 工具集合
     * @return 受控进程参数
     */
    @Bean
    @ConditionalOnProperty(name = "dpom.codegraph.enabled", havingValue = "true")
    public CodeGraphProcessParameters codeGraphProcessParameters(
            @Value("${dpom.codegraph.executable-path:}") String executablePath,
            @Value("${dpom.codegraph.mcp-tools:explore,status,node,search,callers,callees,impact,files}") String mcpTools) {
        return new CodeGraphProcessParameters(executablePath, mcpTools);
    }

    /**
     * 版本校验器（development 装配）。
     *
     * @return 版本校验器
     */
    @Bean
    @ConditionalOnProperty(name = "dpom.codegraph.enabled", havingValue = "true")
    public CodeGraphVersionValidator codeGraphVersionValidator() {
        return new CodeGraphVersionValidator();
    }

    /**
     * development：在创建 stdio 客户端前校验 executable 存在且版本匹配，失败 fail closed。
     *
     * @param repositoryRegistry 仓库注册表（快照根解析 + projectPath 校验）
     * @param params            受控进程参数
     * @param versionValidator  版本校验器
     * @param executablePath    CodeGraph 可执行文件路径
     * @param version           固定版本
     * @return 代码图客户端
     */
    @Bean
    @ConditionalOnProperty(name = "dpom.codegraph.enabled", havingValue = "true")
    public CodeGraphClient codeGraphClient(RepositoryRegistry repositoryRegistry,
                                            CodeGraphProcessParameters params,
                                            CodeGraphVersionValidator versionValidator,
                                            @Value("${dpom.codegraph.executable-path:}") String executablePath,
                                            @Value("${dpom.codegraph.version:1.5.0}") String version) {
        versionValidator.validate(Path.of(executablePath), version);
        return new CodeGraphStdioClient(() -> {
            StdioClientTransport transport = new StdioClientTransport(params.toServerParameters());
            return McpClient.sync(transport).build();
        }, repositoryRegistry);
    }

    /**
     * production：禁用态 port（无 stdio 子进程、无源码访问，调用 fail closed；不是 CodeGraph adapter）。
     *
     * @return 禁用态代码图客户端
     */
    @Bean
    @ConditionalOnProperty(name = "dpom.codegraph.enabled", havingValue = "false", matchIfMissing = true)
    public CodeGraphClient disabledCodeGraphClient() {
        return new DisabledCodeGraphClient();
    }
}
