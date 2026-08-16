package com.dpom.agent.web;

import com.dpom.agent.adapter.llm.FakeModelClient;
import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.Symbol;
import com.dpom.agent.common.llm.ChatMessage;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.llm.ModelTurnResult;
import com.dpom.agent.common.llm.Role;
import com.dpom.agent.common.llm.ToolInvocation;
import com.dpom.agent.common.runtime.ArtifactRef;
import com.dpom.agent.common.runtime.ObservationInput;
import com.dpom.agent.common.runtime.RuntimeEvidenceClient;
import com.dpom.agent.core.hypothesis.Hypothesis;
import com.dpom.agent.core.hypothesis.HypothesisStatus;
import com.dpom.agent.core.persistence.command.IncidentInsert;
import com.dpom.agent.core.persistence.command.InvestigationInsert;
import com.dpom.agent.core.investigation.InvestigationCoordinator;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.investigation.SymptomBrain;
import com.dpom.agent.core.observation.Observation;
import com.dpom.agent.core.persistence.HypothesisDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.core.persistence.ObservationDao;
import com.dpom.agent.core.tool.InvestigationToolExecutor;
import com.dpom.agent.core.workspace.CodeWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 症状驱动假设循环验收：多假设、使用 runtime/code 工具、证伪错误假设、证据不足等待人工。
 */
@SpringBootTest
class SymptomInvestigationTest {

    @Autowired
    private IncidentDao incidentDao;

    @Autowired
    private InvestigationDao investigationDao;

    @Autowired
    private HypothesisDao hypothesisDao;

    @Autowired
    private ObservationDao observationDao;

    @Autowired
    private InvestigationCoordinator coordinator;

    /**
     * 跑通“创建设备成功但数据库无记录”的症状驱动调查。
     */
    @Test
    void symptomDrivenLoopFormsAndInvalidatesHypotheses(@TempDir Path tempDir) {
        long investigationId = createInvestigation();

        CodeGraphClient codeGraphClient = mock(CodeGraphClient.class);
        when(codeGraphClient.findCallers("s1", "AssetRepository.insert"))
                .thenReturn(List.of(new Symbol("AssetService.create", "method", "AssetService.java", 35)));
        RuntimeEvidenceClient runtimeClient = mock(RuntimeEvidenceClient.class);
        when(runtimeClient.searchLogs("asset-service", "prod", "INSERT", "1h"))
                .thenReturn(List.of(new ObservationInput(
                        new ArtifactRef("logs", "l1", "asset-service"), "INSERT 失败日志", "{}")));

        InvestigationToolExecutor executor = new InvestigationToolExecutor(
                "s1", tempDir, "asset-service", "prod", codeGraphClient, new CodeWorkspace(), runtimeClient);
        SymptomBrain brain = new SymptomBrain(scriptedLlm(), "创建设备成功，但数据库没有数据");

        coordinator.run(investigationId, brain, executor);

        assertThat(investigationDao.findById(investigationId).orElseThrow().status())
                .isEqualTo(InvestigationStatus.WAITING_FOR_HUMAN);

        List<Hypothesis> hypotheses = hypothesisDao.findByInvestigationId(investigationId);
        assertThat(hypotheses).hasSizeGreaterThanOrEqualTo(2);
        assertThat(hypotheses).anyMatch(h -> h.status() == HypothesisStatus.INVALIDATED);

        List<Observation> observations = observationDao.findByInvestigationId(investigationId);
        assertThat(observations).extracting(Observation::source).contains("runtime", "codegraph");
    }

    /**
     * 脚本化 LLM：先形成假设，再用 runtime/code 工具取证，证伪错误假设，最后等待人工。
     */
    private ModelClient scriptedLlm() {
        AtomicInteger turn = new AtomicInteger(0);
        return new FakeModelClient(request -> {
            int t = turn.incrementAndGet();
            try {
                return switch (t) {
                    case 1 -> new ModelTurnResult(ChatMessage.assistant(
                            "{\"type\":\"update\",\"newHypotheses\":[\"请求未到达\",\"INSERT 后事务回滚\",\"写入成功但查询过滤错误\"]}"));
                    case 2 -> new ModelTurnResult(ChatMessage.assistantToolCalls(
                            List.of(new ToolInvocation("call-2", "search_logs", "{\"keyword\":\"INSERT\"}"))));
                    case 3 -> new ModelTurnResult(ChatMessage.assistant(
                            "{\"type\":\"update\",\"updates\":[{\"id\":" + rollbackHypothesisId(request)
                                    + ",\"status\":\"INVALIDATED\"}]}"));
                    case 4 -> new ModelTurnResult(ChatMessage.assistantToolCalls(
                            List.of(new ToolInvocation("call-4", "find_callers", "{\"symbol\":\"AssetRepository.insert\"}"))));
                    default -> new ModelTurnResult(ChatMessage.assistant(
                            "{\"type\":\"wait\",\"reason\":\"证据不足，需要人工\"}"));
                };
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 从上下文文本中找到“回滚”假设的 id。
     */
    private long rollbackHypothesisId(com.dpom.agent.common.llm.ModelTurnRequest request) {
        String userText = request.messages().stream()
                .filter(message -> message.role() == Role.USER)
                .findFirst()
                .map(ChatMessage::content)
                .orElse("");
        for (String line : userText.split("\n")) {
            int idStart = line.indexOf("[id=");
            if (idStart >= 0 && line.contains("回滚")) {
                int idEnd = line.indexOf("]", idStart);
                return Long.parseLong(line.substring(idStart + 4, idEnd));
            }
        }
        return -1;
    }

    /**
     * 创建一条调查。
     */
    private long createInvestigation() {
        IncidentInsert incidentCommand = new IncidentInsert("asset-service", "prod", "1.0.0", "abc123",
                "创建设备成功，但数据库没有数据");
        incidentDao.insert(incidentCommand);
        InvestigationInsert investigationCommand = new InvestigationInsert(incidentCommand.getId(),
                InvestigationStatus.CREATED, null, 50, 100, 1800, 5);
        investigationDao.insert(investigationCommand);
        return investigationCommand.getId();
    }
}
