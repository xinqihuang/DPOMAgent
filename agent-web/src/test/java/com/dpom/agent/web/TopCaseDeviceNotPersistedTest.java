package com.dpom.agent.web;

import com.dpom.agent.adapter.llm.FakeModelClient;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.common.llm.ChatMessage;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.llm.ModelTurnRequest;
import com.dpom.agent.common.llm.ModelTurnResult;
import com.dpom.agent.common.llm.Role;
import com.dpom.agent.common.llm.ToolInvocation;
import com.dpom.agent.common.runtime.ArtifactRef;
import com.dpom.agent.common.runtime.ObservationInput;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;
import com.dpom.agent.core.conclusion.Conclusion;
import com.dpom.agent.core.incident.Incident;
import com.dpom.agent.core.investigation.Investigation;
import com.dpom.agent.core.investigation.InvestigationCoordinator;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.investigation.SymptomBrain;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.HypothesisDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.tool.InvestigationToolExecutor;
import com.dpom.agent.core.workspace.CodeWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TOP 案例验收：资产界面创建设备成功但数据库无记录（四个无堆栈场景）。
 */
@SpringBootTest
class TopCaseDeviceNotPersistedTest {

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    @Autowired
    private HypothesisDao hypothesisDao;

    @Autowired
    private ConclusionDao conclusionDao;

    @Autowired
    private InvestigationCoordinator coordinator;

    /**
     * Case A：业务分支提前 return。
     */
    @Test
    void caseA_businessBranchEarlyReturn(@TempDir Path tempDir) throws Exception {
        buildWorkspace(tempDir, "void create(Device d){ if(d.disabled()){ return; } repo.insert(d); }",
                "void insert(Device d){ /* insert */ }");
        long id = runCase(tempDir, "业务分支提前 return", "事务回滚",
                "AssetService.create 业务分支提前返回，未调用 Repository.insert", "AssetService.java",
                "未发现 INSERT 日志");

        assertRootCause(id, "AssetService.create");
    }

    /**
     * Case B：INSERT 后事务回滚。
     */
    @Test
    void caseB_transactionRollback(@TempDir Path tempDir) throws Exception {
        buildWorkspace(tempDir, "@Transactional void create(Device d){ repo.insert(d); throw new RuntimeException(); }",
                "void insert(Device d){ /* insert */ }");
        long id = runCase(tempDir, "INSERT 后事务回滚", "业务分支提前 return",
                "AssetService.create 事务回滚导致 INSERT 未提交", "AssetService.java", "出现 INSERT 日志随后异常回滚");

        assertRootCause(id, "AssetService.create");
    }

    /**
     * Case C：错误 tenant/schema。
     */
    @Test
    void caseC_wrongTenant(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("AssetController.java"), "class AssetController {}\n");
        Files.writeString(tempDir.resolve("AssetService.java"), "class AssetService {}\n");
        Files.writeString(tempDir.resolve("application.yml"), "datasource:\n  tenant: tenant_b\n");
        long id = runCase(tempDir, "datasource/schema/tenant 配置错误", "事务回滚",
                "datasource 指向了错误的 tenant", "application.yml", "无目标库写入");

        assertRootCause(id, "tenant");
    }

    /**
     * Case D：写入成功但查询过滤错误。
     */
    @Test
    void caseD_readSideFilter(@TempDir Path tempDir) throws Exception {
        buildWorkspace(tempDir, "void create(Device d){ repo.insert(d); }",
                "List<Device> find(){ return query(\"status = 'DELETED'\"); }");
        long id = runCase(tempDir, "写入成功但查询过滤错误", "事务回滚",
                "AssetRepository.find 查询过滤条件错误导致读取不到", "AssetRepository.java", "INSERT 成功但查询为空");

        assertRootCause(id, "AssetRepository.find");
    }

    /**
     * 断言根因结论引用观察且代码位置与预期对齐。
     */
    private void assertRootCause(long investigationId, String expectedLocation) {
        assertThat(investigationDao.findById(investigationId).orElseThrow().status())
                .isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(hypothesisDao.findByInvestigationId(investigationId)).hasSizeGreaterThanOrEqualTo(2);
        Conclusion conclusion = conclusionDao.findByInvestigationId(investigationId).orElseThrow();
        assertThat(conclusion.evidenceIds()).isNotBlank();
        assertThat(conclusion.rootCause()).contains(expectedLocation);
    }

    /**
     * 运行一个案例：形成两个假设、用 code+runtime 取证、证伪错误假设、给出结论。
     */
    private long runCase(Path workspace, String correctHypothesis, String wrongHypothesis, String rootCause,
                         String readSourcePath, String logEvidence) {
        CodeGraphClient codeGraphClient = mock(CodeGraphClient.class);
        when(codeGraphClient.findCallers("s1", "AssetRepository.insert"))
                .thenReturn(List.of(new Symbol("AssetService.create", "method", "AssetService.java", 30)));
        RuntimeEvidenceClient runtimeClient = mock(RuntimeEvidenceClient.class);
        when(runtimeClient.searchLogs("asset-service", "prod", "INSERT", "1h"))
                .thenReturn(List.of(new ObservationInput(new ArtifactRef("logs", "l1", "asset-service"), logEvidence, "{}")));

        InvestigationToolExecutor executor = new InvestigationToolExecutor(
                "s1", workspace, "asset-service", "prod", codeGraphClient, new CodeWorkspace(), runtimeClient);
        ModelClient llm = scriptedLlm(correctHypothesis, wrongHypothesis, rootCause, readSourcePath);
        SymptomBrain brain = new SymptomBrain(llm, "创建设备成功，但数据库没有数据");

        long investigationId = createInvestigation();
        coordinator.run(investigationId, brain, executor);
        return investigationId;
    }

    /**
     * 脚本化 LLM。
     */
    private ModelClient scriptedLlm(String correct, String wrong, String rootCause, String readSourcePath) {
        AtomicInteger turn = new AtomicInteger(0);
        return new FakeModelClient(request -> {
            int t = turn.incrementAndGet();
            try {
                return switch (t) {
                    case 1 -> new ModelTurnResult(ChatMessage.assistant(
                            "{\"type\":\"update\",\"newHypotheses\":[\"" + correct + "\",\"" + wrong + "\"]}"));
                    case 2 -> new ModelTurnResult(ChatMessage.assistantToolCalls(
                            List.of(new ToolInvocation("c2", "read_source", "{\"path\":\"" + readSourcePath + "\"}"))));
                    case 3 -> new ModelTurnResult(ChatMessage.assistant(
                            "{\"type\":\"update\",\"updates\":[{\"id\":"
                                    + hypothesisId(request, wrong) + ",\"status\":\"INVALIDATED\"}]}"));
                    case 4 -> new ModelTurnResult(ChatMessage.assistantToolCalls(
                            List.of(new ToolInvocation("c4", "search_logs", "{\"keyword\":\"INSERT\"}"))));
                    default -> new ModelTurnResult(ChatMessage.assistant(
                            "{\"type\":\"conclude\",\"resultType\":\"ROOT_CAUSE_FOUND\",\"rootCause\":\""
                                    + rootCause + "\",\"summary\":\"定位到根因\",\"evidenceIds\":\""
                                    + evidenceIds(request) + "\"}"));
                };
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 从上下文文本找指定描述假设的 id。
     */
    private long hypothesisId(ModelTurnRequest request, String description) {
        for (String line : userText(request).split("\n")) {
            int idStart = line.indexOf("[id=");
            if (idStart >= 0 && line.contains(description)) {
                int idEnd = line.indexOf("]", idStart);
                return Long.parseLong(line.substring(idStart + 4, idEnd));
            }
        }
        return -1;
    }

    /**
     * 收集上下文文本中的观察 id。
     */
    private String evidenceIds(ModelTurnRequest request) {
        List<String> ids = new java.util.ArrayList<>();
        for (String line : userText(request).split("\n")) {
            String marker = "--- 观察 id=";
            int idx = line.indexOf(marker);
            if (idx >= 0) {
                int start = idx + marker.length();
                int end = line.indexOf(" ", start);
                if (end < 0) {
                    end = line.length();
                }
                ids.add(line.substring(start, end));
            }
        }
        return String.join(",", ids);
    }

    /**
     * 提取用户消息文本。
     */
    private String userText(ModelTurnRequest request) {
        return request.messages().stream()
                .filter(message -> message.role() == Role.USER)
                .findFirst()
                .map(ChatMessage::content)
                .orElse("");
    }

    /**
     * 构建工作区。
     */
    private void buildWorkspace(Path dir, String serviceContent, String repositoryContent) throws Exception {
        Files.writeString(dir.resolve("AssetController.java"), "class AssetController { void create(){ service.create(); } }\n");
        Files.writeString(dir.resolve("AssetService.java"), serviceContent + "\n");
        Files.writeString(dir.resolve("AssetRepository.java"), repositoryContent + "\n");
    }

    /**
     * 创建调查。
     */
    private long createInvestigation() {
        long incidentId = incidentDao.insert(new Incident(
                null, "asset-service", "prod", "1.0.0", "abc123", "创建设备成功，但数据库没有数据", null));
        return investigationDao.insert(new Investigation(
                null, incidentId, InvestigationStatus.CREATED, null, 50, 100, 1800, 5, null, null));
    }
}
