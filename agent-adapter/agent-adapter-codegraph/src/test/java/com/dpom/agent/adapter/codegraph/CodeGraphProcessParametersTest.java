package com.dpom.agent.adapter.codegraph;

import io.modelcontextprotocol.client.transport.ServerParameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 受控进程参数测试：参数与环境由代码固定，遥测与更新检查禁用，无调用方注入面。
 */
class CodeGraphProcessParametersTest {

    @Test
    void buildsFixedArgs() {
        ServerParameters params = new CodeGraphProcessParameters("C:\\tools\\codegraph.exe",
                "explore,status,node,search,callers,callees,impact,files").toServerParameters();

        assertThat(params.getCommand()).isEqualTo("C:\\tools\\codegraph.exe");
        assertThat(params.getArgs()).containsExactly("serve", "--mcp");
    }

    @Test
    void disablesTelemetryAndUpdateCheck() {
        ServerParameters params = new CodeGraphProcessParameters("codegraph", "explore,search").toServerParameters();
        Map<String, String> env = params.getEnv();

        assertThat(env.get(CodeGraphProcessParameters.ENV_TELEMETRY)).isEqualTo("0");
        assertThat(env.get(CodeGraphProcessParameters.ENV_DO_NOT_TRACK)).isEqualTo("1");
        assertThat(env.get(CodeGraphProcessParameters.ENV_NO_UPDATE_CHECK)).isEqualTo("1");
    }

    @Test
    void setsExplicitMcpTools() {
        ServerParameters params = new CodeGraphProcessParameters("codegraph", "explore,search").toServerParameters();

        assertThat(params.getEnv().get(CodeGraphProcessParameters.ENV_MCP_TOOLS)).isEqualTo("explore,search");
    }

    @Test
    void argsAreFixedNotCallerControlled() {
        List<String> args = new CodeGraphProcessParameters("codegraph", "x").toServerParameters().getArgs();

        // 参数列表由代码固定为 serve --mcp，不含任何调用方可控的命令/参数
        assertThat(args).containsExactly("serve", "--mcp");
        assertThat(args).doesNotContain("cmd.exe", "powershell", "sh", "bash");
    }
}
