package com.dpom.agent.core.logevidence;

import com.dpom.agent.common.codegraph.CodeGraphClient;
import com.dpom.agent.common.codegraph.CodeSnapshot;
import com.dpom.agent.common.logtemplate.LogParseResult;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.dpom.agent.core.workspace.CodeWorkspace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 日志证据服务：编排「有界摄入 → 前缀分离 → Drain3 → 聚合 → 锚点 → 版本绑定源码解析 → 证据束」，供调查循环消费。
 */
public class LogEvidenceService {

    private final LogTemplateMinerClient miner;
    private final CodeEvidenceResolver resolver;
    private final EvidenceBundleBuilder builder;
    private final BoundedLogIntake intake = new BoundedLogIntake();
    private final LogPrefixSplitter splitter = new LogPrefixSplitter();
    private final LogEvidenceAggregator aggregator = new LogEvidenceAggregator();
    private final CodeAnchorExtractor extractor = new CodeAnchorExtractor();
    private final LogRedactor redactor = new LogRedactor();

    /**
     * 构造服务。
     *
     * @param miner           日志模板挖掘客户端（Drain3）
     * @param codeGraphClient 代码图客户端
     * @param workspace       受控代码工作区
     * @param builder         证据束构建器
     */
    public LogEvidenceService(LogTemplateMinerClient miner, CodeGraphClient codeGraphClient, CodeWorkspace workspace,
                              EvidenceBundleBuilder builder) {
        this.miner = miner;
        this.resolver = new CodeEvidenceResolver(codeGraphClient, workspace);
        this.builder = builder;
    }

    /**
     * 运行日志到代码证据管道。
     *
     * @param service      服务编码
     * @param environment  环境
     * @param release      发布版本
     * @param commit       提交 SHA
     * @param timeRange    时间范围
     * @param minerVersion 挖掘器版本
     * @param snapshot     代码快照
     * @param rawLogs      原始日志行
     * @return 证据束
     */
    public EvidenceBundle run(String service, String environment, String release, String commit, String timeRange,
                              String minerVersion, CodeSnapshot snapshot, List<String> rawLogs) {
        LogIntakeResult intakeResult = intake.intake(rawLogs, LogIntakeLimits.defaults());
        List<StructuredLog> structured = new ArrayList<>();
        for (String line : intakeResult.lines()) {
            StructuredLog s = splitter.split(line);
            structured.add(new StructuredLog(s.timestamp(), s.level(), s.logger(), redactor.redact(s.message())));
        }
        List<String> messages = structured.stream().map(StructuredLog::message).toList();
        List<LogParseResult> results;
        List<String> degradations = new ArrayList<>();
        try {
            results = miner.parseLogs(messages);
        } catch (RuntimeException e) {
            degradations.add("LOG_MINER_UNAVAILABLE");
            results = fallback(messages);
        }
        List<LogEvidence> logs = aggregator.aggregate(structured, results, service, environment, release, commit,
                timeRange, minerVersion, LogIntakeLimits.defaults());
        List<CodeAnchor> anchors = new ArrayList<>();
        for (LogEvidence evidence : logs) {
            anchors.addAll(extractor.extract(evidence));
        }
        anchors.addAll(loggerAnchors(structured));
        List<CodeEvidence> codes = resolver.resolve(commit, snapshot, anchors);
        for (CodeEvidence code : codes) {
            if (!"VERIFIED".equals(code.status())) {
                degradations.add(code.status());
            }
        }
        return builder.build(service, environment, release, commit, timeRange, logs, anchors, codes, degradations,
                List.of());
    }

    /**
     * 从结构化前缀收集 logger 锚点（logger 已在 T102 从 message 分离，此处补回作为代码锚点）。
     */
    private List<CodeAnchor> loggerAnchors(List<StructuredLog> structured) {
        Set<String> loggers = new LinkedHashSet<>();
        for (StructuredLog s : structured) {
            if (s.logger() != null && !s.logger().isBlank()) {
                loggers.add(s.logger());
            }
        }
        List<CodeAnchor> out = new ArrayList<>();
        for (String logger : loggers) {
            out.add(new CodeAnchor("LOGGER", logger, "log-evidence", 0.7, "v1"));
        }
        return out;
    }

    /**
     * Drain3 不可用时的兜底：每条消息作为一个独立簇，避免丢失有界日志。
     */
    private List<LogParseResult> fallback(List<String> messages) {
        List<LogParseResult> out = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            out.add(new LogParseResult(i, 1, messages.get(i), List.of()));
        }
        return out;
    }
}
