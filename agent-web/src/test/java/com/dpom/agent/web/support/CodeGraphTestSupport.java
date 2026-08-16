package com.dpom.agent.web.support;

import com.dpom.agent.adapter.codegraph.CodeGraphProcessParameters;
import com.dpom.agent.adapter.codegraph.CodeGraphStdioClient;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.RepositoryRegistry;
import com.dpom.agent.web.config.ConfigRepositoryRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.StdioClientTransport;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试专用 CodeGraph stdio 客户端构造器：真实 E2E 由环境变量显式启用时使用；
 * 仓库注册表为 ConfigRepositoryRegistry，projectPath 受 resolve/反查校验。
 */
public final class CodeGraphTestSupport {

    /** 测试夹具使用的快照 commit。 */
    public static final String SNAPSHOT_COMMIT = "snapshot";

    private CodeGraphTestSupport() {
    }

    /**
     * 构造 stdio CodeGraph 客户端与基于映射的仓库注册表。
     *
     * @param executable CodeGraph 可执行文件路径
     * @param repos      serviceCode → 快照根目录映射
     * @return 代码图客户端
     */
    public static CodeGraphClient stdioClient(String executable, Map<String, Path> repos) {
        Map<String, ConfigRepositoryRegistry.Entry> entries = new LinkedHashMap<>();
        for (Map.Entry<String, Path> e : repos.entrySet()) {
            entries.put(e.getKey(), new ConfigRepositoryRegistry.Entry(null, SNAPSHOT_COMMIT, e.getValue()));
        }
        RepositoryRegistry registry = new ConfigRepositoryRegistry(entries, null);
        return new CodeGraphStdioClient(() -> {
            StdioClientTransport transport = new StdioClientTransport(new CodeGraphProcessParameters(executable,
                    "explore,status,node,search,callers,callees,impact,files").toServerParameters());
            return McpClient.sync(transport).build();
        }, registry);
    }
}
