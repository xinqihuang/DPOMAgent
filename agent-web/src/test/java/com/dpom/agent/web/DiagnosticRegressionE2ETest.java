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
import com.dpom.agent.core.eval.BenchmarkCaseResult;
import com.dpom.agent.core.eval.BenchmarkMetrics;
import com.dpom.agent.core.eval.BenchmarkResultWriter;
import com.dpom.agent.core.eval.ConclusionEvaluation;
import com.dpom.agent.core.eval.ConclusionEvaluator;
import com.dpom.agent.core.eval.EvalCase;
import com.dpom.agent.core.eval.EvalFixtureLoader;
import com.dpom.agent.core.eval.FixtureValidator;
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
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 真实诊断回归套件：对 E01/E03/E05 跑真实 Drain3 → CGC → snapshot → EvidenceBundle → DeepSeek → Conclusion，
 * 逐案例独立记录并写出 diagnostic-regression.json 指标。由 DPOM_E2E_FULL=true 显式启用，默认跳过。
 */
@EnabledIfEnvironmentVariable(named = "DPOM_E2E_FULL", matches = "true")
@SpringBootTest
class DiagnosticRegressionE2ETest {

    private static final List<String> CASE_DIRS = List.of(
            "E01-device-transaction-rollback", "E03-telemetry-partial-batch-loss",
            "E05-downstream-timeout-retry-storm");

    @Autowired private IncidentDao incidentDao;
    @Autowired private InvestigationDao investigationDao;
    @Autowired private ConclusionDao conclusionDao;
    @Autowired private EvidenceBundleDao bundleDao;
    @Autowired private InvestigationStepDao stepDao;
    @Autowired private InvestigationCoordinator coordinator;

    private static Path caseDir(String dir) {
        return Path.of("..", "evals", "cases", dir).toAbsolutePath().normalize();
    }

    @Test
    void runsAllCasesAndWritesBenchmark() throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        assertThat(apiKey).as("需要 DEEPSEEK_API_KEY").isNotBlank();
        Path out = Path.of("target", "e2e-results", "diagnostic-regression.json").toAbsolutePath().normalize();
        Files.deleteIfExists(out);

        ModelClient llm = new DeepSeekModelClient(RestClient.builder().baseUrl("https://api.deepseek.com")
                .defaultHeader("Authorization", "Bearer " + apiKey).build(), "deepseek-v4-pro");
        CodeGraphClient cgc = new McpCodeGraphClient(() ->
                McpClient.sync(HttpClientSseClientTransport.builder("http://localhost:8080")
                        .sseEndpoint("/api/v1/mcp/sse").build()).build());
        CodeWorkspace ws = new CodeWorkspace();
        LogTemplateMinerClient miner = new McpLogTemplateMinerClient(() ->
                McpClient.sync(HttpClientSseClientTransport.builder("http://localhost:8100").build()).build());

        List<BenchmarkCaseResult> results = new ArrayList<>();
        for (String dir : CASE_DIRS) {
            results.add(runCase(dir, llm, cgc, ws, miner));
        }
        BenchmarkMetrics metrics = BenchmarkMetrics.compute(results);
        writeResult(results, metrics, out);
        assertThat(metrics.overallPassed()).as("全部 mandatory 案例必须执行且通过").isTrue();
    }

    private BenchmarkCaseResult runCase(String dir, ModelClient llm, CodeGraphClient cgc, CodeWorkspace ws,
                                       LogTemplateMinerClient miner) {
        long start = System.currentTimeMillis();
        try {
            EvalCase c = new EvalFixtureLoader().load(caseDir(dir));
            List<String> leaks = new FixtureValidator().validate(c);
            if (!leaks.isEmpty()) {
                return new BenchmarkCaseResult(dir, true, false, "FAILED", null, null, c.expected().rootCauseId(),
                        List.of(), List.of(), List.of(), 0, System.currentTimeMillis() - start, leaks);
            }
            CodeSnapshot snapshot = cgc.resolveSnapshot(c.serviceCode(), c.commit());
            LogEvidenceService service = new LogEvidenceService(miner, cgc, ws, new EvidenceBundleBuilder(1_000_000));
            EvidenceBundle bundle = service.run(c.serviceCode(), c.environment(), c.release(), c.commit(), "1h",
                    "drain3-mcp-0.9", snapshot, c.logs());
            long id = createInvestigation(c);
            bundleDao.save(id, bundle);
            InvestigationToolExecutor executor = new InvestigationToolExecutor(snapshot.snapshotId(),
                    Path.of(snapshot.workspacePath()), c.serviceCode(), c.environment(), cgc, ws,
                    mock(RuntimeEvidenceClient.class), miner);
            coordinator.run(id, new SymptomBrain(llm, c.symptom()), executor);
            Conclusion conclusion = conclusionDao.findByInvestigationId(id).orElseThrow();
            InvestigationStatus status = investigationDao.findById(id).orElseThrow().status();
            ConclusionEvaluation eval = new ConclusionEvaluator().evaluate(conclusion, bundle, c.expected());
            long toolCalls = stepDao.findByInvestigationId(id).stream().filter(s -> "TOOL".equals(s.stepType())).count();
            boolean passed = status == InvestigationStatus.COMPLETED && eval.passed()
                    && "ROOT_CAUSE_FOUND".equals(conclusion.resultType());
            return new BenchmarkCaseResult(dir, true, passed, status.name(), conclusion.resultType(),
                    conclusion.rootCauseId(), c.expected().rootCauseId(), eval.logEvidenceIds(),
                    eval.sourceEvidenceIds(), eval.expectedSymbolsMatched(), toolCalls,
                    System.currentTimeMillis() - start, eval.failures());
        } catch (Exception e) {
            return new BenchmarkCaseResult(dir, true, false, "FAILED", null, null, null, List.of(), List.of(),
                    List.of(), 0, System.currentTimeMillis() - start, List.of("EXCEPTION:" + e));
        }
    }

    private long createInvestigation(EvalCase c) {
        long incidentId = incidentDao.insert(new Incident(null, c.serviceCode(), c.environment(), c.release(),
                c.commit(), c.symptom(), null));
        return investigationDao.insert(new Investigation(null, incidentId, InvestigationStatus.CREATED, null, 30, 60,
                1800, 5, null, null));
    }

    private void writeResult(List<BenchmarkCaseResult> results, BenchmarkMetrics metrics, Path out) throws Exception {
        new BenchmarkResultWriter().write(results, metrics, out, "deepseek-v4-pro", "v1", "v1", "v1", "drain3-mcp-0.9");
    }
}
