package com.dpom.agent.adapter.codegraph;

import io.modelcontextprotocol.client.transport.ServerParameters;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CodeGraph 受控进程参数：executable 路径来自服务端配置，参数与遥测禁用环境由代码固定构造，
 * 不暴露任何调用方可控的命令/参数/环境输入。
 */
public final class CodeGraphProcessParameters {

    /** 固定参数：stdio MCP 服务。 */
    private static final List<String> FIXED_ARGS = List.of("serve", "--mcp");

    /** 遥测关闭。 */
    public static final String ENV_TELEMETRY = "CODEGRAPH_TELEMETRY";
    /** 不跟踪。 */
    public static final String ENV_DO_NOT_TRACK = "DO_NOT_TRACK";
    /** 禁止运行时更新检查。 */
    public static final String ENV_NO_UPDATE_CHECK = "CODEGRAPH_NO_UPDATE_CHECK";
    /** 显式 MCP 工具集合。 */
    public static final String ENV_MCP_TOOLS = "CODEGRAPH_MCP_TOOLS";

    private final String executablePath;
    private final String mcpTools;

    /**
     * 构造受控进程参数。
     *
     * @param executablePath CodeGraph 可执行文件路径（服务端配置）
     * @param mcpTools       CODEGRAPH_MCP_TOOLS 工具集合（服务端配置）
     */
    public CodeGraphProcessParameters(String executablePath, String mcpTools) {
        this.executablePath = executablePath;
        this.mcpTools = mcpTools;
    }

    /**
     * 构建 MCP SDK 的 ServerParameters。
     *
     * @return ServerParameters（命令 + 固定参数 + 固定环境）
     */
    public ServerParameters toServerParameters() {
        return ServerParameters.builder(executablePath)
                .args(FIXED_ARGS)
                .env(fixedEnvironment())
                .build();
    }

    /**
     * 固定的进程环境：遥测关闭、不跟踪、禁止更新检查、显式工具集合。
     *
     * @return 固定环境变量
     */
    private Map<String, String> fixedEnvironment() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put(ENV_TELEMETRY, "0");
        env.put(ENV_DO_NOT_TRACK, "1");
        env.put(ENV_NO_UPDATE_CHECK, "1");
        env.put(ENV_MCP_TOOLS, mcpTools);
        return env;
    }
}
