package com.dpom.agent.web;

import com.dpom.agent.adapter.codegraph.McpCodeGraphClient;
import com.dpom.agent.adapter.llm.DeepSeekModelClient;
import com.dpom.agent.adapter.runtime.McpLogTemplateMinerClient;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;
import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.eval.ConclusionEvaluation;
import com.dpom.agent.core.eval.ConclusionEvaluator;
import com.dpom.agent.core.eval.EvalCase;
import com.dpom.agent.core.eval.EvalFixtureLoader;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationCoordinator;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.investigation.SymptomBrain;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.EvidenceBundleBuilder;
import com.dpom.agent.core.logevidence.LogEvidenceService;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.EvidenceBundleDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.InvestigationStepDao;
import com.dpom.agent.core.tool.InvestigationToolExecutor;
import com.dpom.agent.core.workspace.CodeWorkspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * T110 真实联合 E2E：真实 Drain3 → CGC → Snapshot → EvidenceBundle → 真实 DeepSeek → 调查循环 → Conclusion，
 * 并对最终 Conclusion + EvidenceBundle 执行 E01 expected.json 断言。
 *
 * <p>由 DPOM_E2E_FULL=true 显式启用，默认跳过。</p>
 */
@EnabledIfEnvironmentVariable(named = "DPOM_E2E_FULL", matches = "true")
@SpringBootTest
class CombinedE2ETest {

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    @Autowired
    private ConclusionDao conclusionDao;

    @Autowired
    private EvidenceBundleDao bundleDao;

    @Autowired
    private InvestigationStepDao stepDao;

    @Autowired
    private InvestigationCoordinator coordinator;

    /**
     * E01 跑通完整链路并对最终结论执行 fixture 断言。
     */
    @Test
    void runsDrain3CodegraphLlmPipeline() throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        assertThat(apiKey).as("需要 DEEPSEEK_API_KEY").isNotBlank();

        Path out = Path.of("target", "e2e-results", "combined-e2e.json").toAbsolutePath().normalize();
        Files.deleteIfExists(out);

        LogTemplateMinerClient miner = new McpLogTemplateMinerClient(() ->
                McpClient.sync(HttpClientSseClientTransport.builder("http://localhost:8100").build()).build());
        CodeGraphClient cgc = new McpCodeGraphClient(() ->
                McpClient.sync(HttpClientSseClientTransport.builder("http://localhost:8080")
                        .sseEndpoint("/api/v1/mcp/sse").build()).build());
        CodeWorkspace workspace = new CodeWorkspace();

        EvalCase e01 = new EvalFixtureLoader().load(
                Path.of("..", "evals", "cases", "E01-device-transaction-rollback").toAbsolutePath().normalize());
        CodeSnapshot snapshot = cgc.resolveSnapshot(e01.serviceCode(), e01.commit());

        LogEvidenceService service = new LogEvidenceService(miner, cgc, workspace, new EvidenceBundleBuilder(1_000_000));
        EvidenceBundle bundle = service.run(e01.serviceCode(), e01.environment(), e01.release(), e01.commit(), "1h",
                "drain3-mcp-0.9", snapshot, e01.logs());
        assertThat(bundle.hasVerifiedSource()).as("需要真实 VERIFIED 源码证据").isTrue();

        long investigationId = createInvestigation(e01);
        bundleDao.save(investigationId, bundle);

        ModelClient llm = new DeepSeekModelClient(RestClient.builder().baseUrl("https://api.deepseek.com")
                .defaultHeader("Authorization", "Bearer " + apiKey).build(), "deepseek-v4-pro");
        InvestigationToolExecutor executor = new InvestigationToolExecutor(snapshot.snapshotId(),
                Path.of(snapshot.workspacePath()), e01.serviceCode(), e01.environment(), cgc, workspace,
                mock(RuntimeEvidenceClient.class), miner);

        long start = System.currentTimeMillis();
        coordinator.run(investigationId, new SymptomBrain(llm, e01.symptom()), executor);
        long latency = System.currentTimeMillis() - start;

        Conclusion conclusion = conclusionDao.findByInvestigationId(investigationId).orElseThrow();
        InvestigationStatus status = investigationDao.findById(investigationId).orElseThrow().status();
        assertThat(status).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(conclusion.resultType()).isEqualTo("ROOT_CAUSE_FOUND");
        assertThat(conclusion.rootCauseId()).isEqualTo(e01.expected().rootCauseId());

        ConclusionEvaluator evaluator = new ConclusionEvaluator();
        ConclusionEvaluation evaluation = evaluator.evaluate(conclusion, bundle, e01.expected());
        assertThat(evaluation.failures()).as("fixture 断言").isEmpty();
        assertThat(evaluation.rootCauseId()).isEqualTo(e01.expected().rootCauseId());

        long toolCalls = stepDao.findByInvestigationId(investigationId).stream()
                .filter(s -> "TOOL".equals(s.stepType())).count();
        Long runId = investigationDao.findById(investigationId).orElseThrow().currentRunId();
        writeResult(e01, conclusion, status, evaluation, toolCalls, latency, runId, out);
    }

    private long createInvestigation(EvalCase c) {
        long incidentId = incidentDao.insert(new Incident(null, c.serviceCode(), c.environment(), c.release(),
                c.commit(), c.symptom(), null));
        return investigationDao.insert(new Investigation(null, incidentId, InvestigationStatus.CREATED, null, 30, 60,
                1800, 5, null, null));
    }

    private void writeResult(EvalCase c, Conclusion conclusion, InvestigationStatus status,
                             ConclusionEvaluation evaluation, long toolCalls, long latency, Long runId, Path out)
            throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executed", true);
        result.put("passed", evaluation.passed());
        result.put("caseId", "E01");
        result.put("model", "deepseek-v4-pro");
        result.put("promptVersion", "v1");
        result.put("toolsetVersion", "v1");
        result.put("ruleVersion", "v1");
        result.put("minerVersion", "drain3-mcp-0.9");
        result.put("runId", runId);
        result.put("commit", c.commit());
        result.put("timestamp", Instant.now().toString());
        result.put("toolCalls", toolCalls);
        result.put("promptTokens", null);
        result.put("completionTokens", null);
        result.put("latencyMs", latency);
        result.put("resultType", conclusion.resultType());
        result.put("rootCauseId", evaluation.rootCauseId());
        result.put("rootCauseDescription", conclusion.rootCause());
        result.put("logEvidenceIds", evaluation.logEvidenceIds());
        result.put("sourceEvidenceIds", evaluation.sourceEvidenceIds());
        result.put("expectedSymbolsMatched", evaluation.expectedSymbolsMatched());
        result.put("investigationStatus", status.name());
        Files.createDirectories(out.getParent());
        Path tmp = out.resolveSibling("combined-e2e.json.tmp");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), result);
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
