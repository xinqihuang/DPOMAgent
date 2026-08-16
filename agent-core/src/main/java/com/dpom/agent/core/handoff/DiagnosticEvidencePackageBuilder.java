package com.dpom.agent.core.handoff;

import com.dpom.agent.core.logevidence.LogRedactor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 诊断证据包构建器：内容 allow-list、脱敏、禁止字段扫描、大小/条数上限，输出确定性逻辑内容。
 */
public class DiagnosticEvidencePackageBuilder {

    /** 允许的 section 名（allow-list，非 deny-list）。 */
    public static final Set<String> ALLOWED_SECTIONS = Set.of(
            "alarm", "timeline", "topology", "logs", "metrics", "code-context",
            "hypotheses", "contradictions", "degradations");

    private final LogRedactor redactor = new LogRedactor();
    private final ForbiddenContentScanner scanner = new ForbiddenContentScanner();
    private final HandoffConfig config;

    /**
     * 构造构建器。
     *
     * @param config 交接配置
     */
    public DiagnosticEvidencePackageBuilder(HandoffConfig config) {
        this.config = config;
    }

    /**
     * 构建证据包逻辑内容。
     *
     * @param packageId  包标识
     * @param service    服务编码
     * @param environment 环境
     * @param release    发布版本
     * @param commit     提交 SHA
     * @param timeRange  时间窗
     * @param sections   allow-list section -> 条目
     * @return 已脱敏、已限量的证据包内容
     */
    public DiagnosticEvidencePackage build(String packageId, String service, String environment, String release,
                                           String commit, String timeRange, Map<String, List<String>> sections) {
        requireText(packageId, "packageId");
        requireText(service, "service");
        requireText(environment, "environment");
        requireText(release, "release");
        requireText(commit, "commit");
        requireText(timeRange, "timeRange");
        if (sections == null || sections.isEmpty()) {
            throw new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "sections required");
        }
        for (String key : sections.keySet()) {
            if (!ALLOWED_SECTIONS.contains(key)) {
                throw new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "section not allowed: " + key);
            }
        }
        Map<String, List<String>> redacted = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        int entries = 0;
        long bytes = 0;
        for (Map.Entry<String, List<String>> e : sections.entrySet()) {
            List<String> values = e.getValue() == null ? List.of() : e.getValue();
            List<String> out = new ArrayList<>(values.size());
            int changed = 0;
            for (String v : values) {
                String text = v == null ? "" : v;
                String r = redactor.redact(text);
                scanner.scan(r);
                if (!r.equals(text)) {
                    changed++;
                }
                out.add(r);
                entries++;
                bytes += r.getBytes(StandardCharsets.UTF_8).length;
            }
            redacted.put(e.getKey(), List.copyOf(out));
            counts.put(e.getKey(), changed);
        }
        if (entries > config.maxPackageEntries()) {
            throw new HandoffException(HandoffErrorCode.ENTRIES_EXCEEDED, "entries exceed limit");
        }
        if (bytes > config.maxPackageBytes()) {
            throw new HandoffException(HandoffErrorCode.SIZE_EXCEEDED, "package bytes exceed limit");
        }
        return new DiagnosticEvidencePackage(config.schemaVersion(), packageId, service, environment, release, commit,
                timeRange, Collections.unmodifiableMap(redacted), Collections.unmodifiableMap(counts));
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new HandoffException(HandoffErrorCode.INVALID_ARGUMENT, field + " required");
        }
    }
}
