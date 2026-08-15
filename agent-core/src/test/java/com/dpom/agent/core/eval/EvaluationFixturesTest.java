package com.dpom.agent.core.eval;

import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.codegraph.SnapshotStatus;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.EvidenceBundleBuilder;
import com.dpom.agent.core.logevidence.LogEvidenceService;
import com.dpom.agent.core.workspace.CodeWorkspace;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T108 评测夹具 E01/E03/E05 验收：输入/录制响应/expected 分离，离线评测断言。
 */
class EvaluationFixturesTest {

    private static final List<String> CASE_DIRS = List.of(
            "E01-device-transaction-rollback", "E03-telemetry-partial-batch-loss", "E05-downstream-timeout-retry-storm");

    private static Path caseDir(String dir) {
        return Path.of("..", "evals", "cases", dir).toAbsolutePath().normalize();
    }

    /**
     * 每个案例含 incident/logs/expected/recorded-drain3/recorded-codegraph/workspace/README。
     */
    @Test
    void eachCaseHasSeparateInputsRecordingsAndWorkspace() {
        for (String dir : CASE_DIRS) {
            Path base = caseDir(dir);
            for (String f : List.of("incident.json", "logs.txt", "expected.json", "README.md",
                    "recorded-drain3.json", "recorded-codegraph.json")) {
                assertThat(Files.exists(base.resolve(f))).as(dir + "/" + f).isTrue();
            }
            assertThat(Files.isDirectory(base.resolve("workspace"))).as(dir + "/workspace").isTrue();
        }
    }

    /**
     * 离线评测：Fake 只读 recorded 文件，断言 rootCauseId/expectedSymbols/requiredEvidenceTypes/forbiddenConclusions。
     */
    @Test
    void offlineEvalPassesAllCases() throws Exception {
        EvalFixtureLoader loader = new EvalFixtureLoader();
        OfflineEvaluator evaluator = new OfflineEvaluator();
        for (String dir : CASE_DIRS) {
            Path base = caseDir(dir);
            EvalCase c = loader.load(base);
            EvidenceBundle bundle = runOffline(c, base);
            assertThat(evaluator.evaluate(c, bundle)).as(dir).isEmpty();
        }
    }

    /**
     * 用录制 Drain3/CodeGraph + 真实工作区运行管道。
     */
    private EvidenceBundle runOffline(EvalCase c, Path caseDir) throws IOException {
        RecordedDrain3Client miner = new RecordedDrain3Client(caseDir.resolve("recorded-drain3.json"));
        RecordedCodeGraphClient cgc = new RecordedCodeGraphClient(caseDir.resolve("recorded-codegraph.json"));
        CodeWorkspace workspace = new CodeWorkspace();
        LogEvidenceService service = new LogEvidenceService(miner, cgc, workspace, new EvidenceBundleBuilder(1_000_000));
        CodeSnapshot snapshot = new CodeSnapshot("s1", c.serviceCode(), c.commit(),
                caseDir.resolve("workspace").toString(), SnapshotStatus.READY);
        return service.run(c.serviceCode(), c.environment(), c.release(), c.commit(), "1h", "drain3-0.9", snapshot,
                c.logs());
    }
}
