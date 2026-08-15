package com.dpom.agent.core.investigation;

import com.dpom.agent.common.llm.ChatMessage;
import com.dpom.agent.common.llm.ModelClient;
import com.dpom.agent.common.llm.ModelTurnRequest;
import com.dpom.agent.common.llm.ModelTurnResult;
import com.dpom.agent.common.llm.ToolInvocation;
import com.dpom.agent.core.hypothesis.Hypothesis;
import com.dpom.agent.core.hypothesis.HypothesisStatus;
import com.dpom.agent.core.logevidence.CodeEvidence;
import com.dpom.agent.core.logevidence.EvidenceBundle;
import com.dpom.agent.core.logevidence.LogEvidence;
import com.dpom.agent.core.observation.Observation;
import com.dpom.agent.core.tool.Toolset;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 症状驱动的 LLM 大脑：根据症状与当前证据，决策工具调用、假设更新、等待人工或结论。
 *
 * <p>LLM 要么发起工具调用，要么输出 JSON 决策（update/wait/conclude）。</p>
 */
public class SymptomBrain implements Brain {

    private static final String SYSTEM_PROMPT = """
            你是研发侧故障调查 Agent，严格按以下规则调查：

            【第一轮：先形成假设，禁止调用任何工具】
            直接输出一条 update JSON 决策，给出 2~5 个候选假设。
            针对异常堆栈类症状，假设必须覆盖：栈顶方法内部逻辑缺陷、上游调用传参/状态错误、
            配置或资源未初始化、版本/依赖不匹配、并发或时序问题。

            【后续每轮：一次只做一个动作】
            每轮要么调用一个工具取证，要么输出一条 JSON 决策，二者不可混用。

            【工具使用规则】
            - read_source 的 path 直接填堆栈里的文件名（例如 ConfigurationFactory.java），
              不要加 workspace/、src/、org/apache/... 等任何前缀；文件名来自 list_files 或堆栈。
            - 堆栈给出「文件名:行号」时，必须带 startLine 读异常抛出点附近的方法体：
              startLine = 行号 - 60（至少 1），例如 ConfigurationFactory.java:305 用 startLine=245。
            - read_source 返回「行号|内容」，行号可与堆栈行号直接对照，定位异常分支（如某一行 return null）。
            - 读完一次源码后，即使内容不完整也必须基于已有内容推进：输出 update 更新假设状态
              （VALIDATING/INVALIDATED/VALIDATED）或直接 conclude；禁止连续多次 read_source 读同一文件。
            - 定位到栈顶类后，用 find_callers / find_callees 佐证调用链。

            【证据与结论规则】
            - 证据不足时，假设只能置 INVALIDATED 或保持 VALIDATING，禁止置 VALIDATED。
            - 读到栈顶方法体后，应能定位异常抛出的具体分支/空值来源，直接输出 conclude
              （resultType=ROOT_CAUSE_FOUND）；只有证据真正不足时才 INCONCLUSIVE，不要略过分析就 INSUFFICIENT_EVIDENCE。
            - 有明确证据后，输出 conclude，resultType 用 ROOT_CAUSE_FOUND，并在 rootCause 里给出
              具体的根因类/方法/触发条件，summary 里给出可复述的因果链条。
            - 若上下文有【日志到代码证据束】：ROOT_CAUSE_FOUND 的 evidenceIds 必须引用至少一条日志证据 id
              和至少一条 VERIFIED 源码证据 id；没有 VERIFIED 源码证据时只能 INCONCLUSIVE 或 wait，不得 ROOT_CAUSE_FOUND。
            - rootCauseId 必须取「异常实际抛出点」对应的类.方法（即堆栈最深帧 / 源码证据中真正抛出异常的那一处），
              不要取上游调用方或业务触发点。例如堆栈含 at AssetRepository.insert(...) 且其方法体抛出异常时，
              rootCauseId 应填 AssetRepository.insert，而不是上层 AssetService.create。

            【JSON 决策格式（每轮只输出一种）】
            {"type":"update","newHypotheses":["..."],"updates":[{"id":1,"status":"INVALIDATED"}]}
            {"type":"wait","reason":"..."}
            {"type":"conclude","resultType":"ROOT_CAUSE_FOUND","rootCauseId":"类.方法","rootCause":"...","summary":"...","evidenceIds":"1,2"}
            """;

    private final ModelClient modelClient;
    private final String symptom;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 构造大脑。
     *
     * @param modelClient 模型客户端
     * @param symptom     症状描述
     */
    public SymptomBrain(ModelClient modelClient, String symptom) {
        this.modelClient = modelClient;
        this.symptom = symptom;
    }

    @Override
    public InvestigationDecision decide(InvestigationContext context) {
        ModelTurnResult result = modelClient.complete(buildRequest(context));
        ChatMessage message = result.message();
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            ToolInvocation call = message.toolCalls().get(0);
            return new InvestigationDecision.Act(new ToolAction(call.name(), call.argumentsJson(), call.name()));
        }
        return parseDecision(message.content());
    }

    /**
     * 构建包含症状、假设与观察的请求。
     */
    private ModelTurnRequest buildRequest(InvestigationContext context) {
        List<ChatMessage> messages = List.of(
                ChatMessage.system(SYSTEM_PROMPT),
                ChatMessage.user(buildContextText(context)));
        return new ModelTurnRequest(messages, Toolset.definitions());
    }

    /**
     * 把上下文编码为可读文本：假设带 id/状态，观察原文呈现源码，便于 LLM 阅读并引用假设 id。
     */
    private String buildContextText(InvestigationContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("【症状】\n").append(symptom).append("\n\n");
        sb.append("【假设】(更新时用 updates[].id 引用)\n");
        if (context.hypotheses().isEmpty()) {
            sb.append("(无)\n");
        }
        for (Hypothesis hypothesis : context.hypotheses()) {
            sb.append("[id=").append(hypothesis.id()).append("][").append(hypothesis.status().name()).append("] ")
                    .append(hypothesis.description()).append("\n");
        }
        sb.append("\n【证据/观察】\n");
        for (Observation observation : context.observations()) {
            sb.append("--- 观察 id=").append(observation.id())
                    .append(" source=").append(observation.source())
                    .append(" location=").append(observation.location() == null ? "" : observation.location())
                    .append(" ---\n");
            sb.append(observation.summary() == null ? "" : observation.summary()).append("\n");
        }
        if (context.evidenceBundle() != null) {
            sb.append(renderBundle(context.evidenceBundle()));
        }
        return sb.toString();
    }

    /**
     * 把证据束渲染为有界、可读、带 provenance 的文本。
     */
    private String renderBundle(EvidenceBundle bundle) {
        StringBuilder sb = new StringBuilder("\n【日志到代码证据束】\n");
        for (LogEvidence e : bundle.logEvidences()) {
            sb.append("- 日志证据 ").append(e.evidenceId()).append(" [")
                    .append(e.summary().severityDistribution().keySet()).append(" x").append(e.summary().count())
                    .append("] ").append(e.summary().template()).append(" (commit=").append(e.commit())
                    .append(", miner=").append(e.minerVersion()).append(")\n");
        }
        for (CodeEvidence e : bundle.codeEvidences()) {
            boolean throwSite = e.anchorValue() != null && e.anchorValue().startsWith("at ");
            sb.append("- 源码证据 ").append(e.evidenceId()).append(" [").append(e.status()).append("] ")
                    .append(e.symbol()).append(" @ ").append(e.filePath()).append(":").append(e.lineNumber())
                    .append(" (commit=").append(e.commit()).append(")");
            if (throwSite) {
                sb.append(" 【异常抛出点】");
            }
            sb.append("\n");
            if (e.excerpt() != null && !e.excerpt().isBlank()) {
                sb.append("    片段: ").append(truncate(e.excerpt(), 200)).append("\n");
            }
        }
        if (!bundle.degradations().isEmpty()) {
            sb.append("- 降级: ").append(String.join(",", bundle.degradations())).append("\n");
        }
        if (bundle.truncated()) {
            sb.append("- 截断: true\n");
        }
        sb.append("结论护栏：ROOT_CAUSE_FOUND 的 evidenceIds 必须引用至少一条日志证据 id 和至少一条 VERIFIED 源码证据 id。\n");
        return sb.toString();
    }

    /**
     * 截断长文本。
     */
    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    /**
     * 解析 JSON 决策。
     */
    private InvestigationDecision parseDecision(String content) {
        try {
            JsonNode node = mapper.readTree(content);
            String type = node.path("type").asText();
            return switch (type) {
                case "update" -> new InvestigationDecision.UpdateHypotheses(
                        strings(node.path("newHypotheses")), updates(node.path("updates")));
                case "wait" -> new InvestigationDecision.WaitForHuman(node.path("reason").asText("需要人工"));
                case "conclude" -> new InvestigationDecision.Conclude(
                        node.path("resultType").asText(), node.path("rootCauseId").asText(),
                        node.path("rootCause").asText(), node.path("summary").asText(),
                        node.path("evidenceIds").asText());
                default -> throw new IllegalStateException("未知决策类型：" + type);
            };
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("解析决策失败：" + content, e);
        }
    }

    /**
     * 解析字符串数组。
     */
    private List<String> strings(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> result.add(item.asText()));
        }
        return result;
    }

    /**
     * 解析假设更新数组。
     */
    private List<HypothesisUpdate> updates(JsonNode node) {
        List<HypothesisUpdate> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                long id = item.path("id").asLong();
                HypothesisStatus status = HypothesisStatus.valueOf(item.path("status").asText());
                result.add(new HypothesisUpdate(id, status));
            }
        }
        return result;
    }
}
