package com.dpom.agent.web;

import com.dpom.agent.web.support.CodeGraphTestSupport;
import com.dpom.agent.adapter.llm.DeepSeekModelClient;
import com.dpom.agent.adapter.runtime.McpLogTemplateMinerClient;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;
import com.dpom.agent.core.hypothesis.Hypothesis;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationCoordinator;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.investigation.SymptomBrain;
import com.dpom.agent.core.observation.Observation;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.HypothesisDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.ObservationDao;
import com.dpom.agent.core.tool.InvestigationToolExecutor;
import com.dpom.agent.core.workspace.CodeWorkspace;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 端到端验收：真实 DeepSeek + 真实 Drain3 MCP + log4j2 工作区，跑一遍堆栈诊断。
 *
 * <p>仅当 DPOM_E2E=true 时运行；需先启动 Drain3 MCP server（--transport sse --port 8100）。</p>
 */
@EnabledIfEnvironmentVariable(named = "DPOM_E2E", matches = "true")
@SpringBootTest
class Log4jStacktraceE2ETest {

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    @Autowired
    private HypothesisDao hypothesisDao;

    @Autowired
    private ObservationDao observationDao;

    @Autowired
    private ConclusionDao conclusionDao;

    @Autowired
    private InvestigationCoordinator coordinator;

    /**
     * 模拟 log4j2 异常堆栈并跑完诊断。
     */
    @Test
    void diagnosesLog4jStacktrace() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        assertThat(apiKey).as("需要 DEEPSEEK_API_KEY 环境变量").isNotBlank();

        ModelClient modelClient = new DeepSeekModelClient(
                RestClient.builder().baseUrl("https://api.deepseek.com")
                        .defaultHeader("Authorization", "Bearer " + apiKey).build(),
                "deepseek-v4-pro");

        LogTemplateMinerClient logTemplateMiner = new McpLogTemplateMinerClient(() ->
                McpClient.sync(HttpClientSseClientTransport.builder("http://localhost:8100").build()).build());

        Path root = Path.of("D:\\code\\log4j2\\log4j-core\\src\\main\\java\\org\\apache\\logging\\log4j\\core\\config");
        CodeGraphClient codeGraphClient = CodeGraphTestSupport.stdioClient(
                System.getenv().getOrDefault("DPOM_CODEGRAPH_EXECUTABLE", "codegraph"),
                Map.of("log4j-core", root));
        RuntimeEvidenceClient runtimeClient = mock(RuntimeEvidenceClient.class);
        InvestigationToolExecutor executor = new InvestigationToolExecutor(
                root.toString(), root, "log4j-core", "prod",
                codeGraphClient, new CodeWorkspace(), runtimeClient, logTemplateMiner);

        String stacktrace = """
                java.lang.IllegalStateException: ConfigurationFactory returned no configuration
                    at org.apache.logging.log4j.core.config.ConfigurationFactory.getConfiguration(ConfigurationFactory.java:305)
                    at org.apache.logging.log4j.core.LoggerContext.reconfigure(LoggerContext.java:617)
                    at org.apache.logging.log4j.core.impl.Log4jContextFactory.getContext(Log4jContextFactory.java:55)
                """;

        long investigationId = createInvestigation(stacktrace);
        SymptomBrain brain = new SymptomBrain(modelClient, stacktrace);
        coordinator.run(investigationId, brain, executor);

        InvestigationStatus status = investigationDao.findById(investigationId).orElseThrow().status();
        System.out.println("===== E2E 诊断结果 =====");
        System.out.println("状态：" + status);
        for (Hypothesis h : hypothesisDao.findByInvestigationId(investigationId)) {
            System.out.println("假设[" + h.status() + "] " + h.description());
        }
        for (Observation o : observationDao.findByInvestigationId(investigationId)) {
            System.out.println("观察[" + o.source() + "] " + (o.summary() == null ? "" : o.summary().substring(0, Math.min(120, o.summary().length()))));
        }
        conclusionDao.findByInvestigationId(investigationId).ifPresent(c ->
                System.out.println("结论[" + c.resultType() + "] " + c.rootCause()));

        assertThat(observationDao.findByInvestigationId(investigationId)).isNotEmpty();
    }

    /**
     * 创建事件与调查。
     */
    private long createInvestigation(String symptom) {
        long incidentId = incidentDao.insert(new Incident(
                null, "log4j-core", "prod", "2.25.x", "7cab23ba", symptom, null));
        return investigationDao.insert(new Investigation(
                null, incidentId, InvestigationStatus.CREATED, null, 30, 60, 1800, 5, null, null));
    }
}
