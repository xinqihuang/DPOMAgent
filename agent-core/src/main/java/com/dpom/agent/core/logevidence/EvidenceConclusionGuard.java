package com.dpom.agent.core.logevidence;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 结论护栏：ROOT_CAUSE_FOUND 必须引用至少一条日志证据与至少一条 VERIFIED 源码证据，且引用不得悬空。
 */
public final class EvidenceConclusionGuard {

    private EvidenceConclusionGuard() {
    }

    /**
     * 校验结论并可能降级。
     *
     * @param bundle      证据束（可为空）
     * @param resultType  结论类型
     * @param evidenceIds 证据 id（逗号分隔，可为空）
     * @return 有效返回原类型；非法 ROOT_CAUSE_FOUND 降级为 INCONCLUSIVE
     */
    public static String validate(EvidenceBundle bundle, String resultType, String evidenceIds) {
        if (!"ROOT_CAUSE_FOUND".equals(resultType)) {
            return resultType;
        }
        // 无证据束时（非日志到代码流程）不启用护栏，保持原结论。
        if (bundle == null) {
            return resultType;
        }
        if (!bundle.hasVerifiedSource()) {
            return "INCONCLUSIVE";
        }
        Set<String> refs = split(evidenceIds);
        Set<String> logIds = bundle.logEvidences().stream().map(LogEvidence::evidenceId).collect(Collectors.toSet());
        Set<String> sourceIds = bundle.codeEvidences().stream()
                .filter(e -> "VERIFIED".equals(e.status())).map(CodeEvidence::evidenceId).collect(Collectors.toSet());
        boolean hasLog = refs.stream().anyMatch(logIds::contains);
        boolean hasSource = refs.stream().anyMatch(sourceIds::contains);
        boolean allExist = refs.stream().allMatch(id -> logIds.contains(id) || sourceIds.contains(id));
        return hasLog && hasSource && allExist ? resultType : "INCONCLUSIVE";
    }

    /**
     * 拆分逗号分隔的 id。
     */
    private static Set<String> split(String ids) {
        if (ids == null || ids.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }
}
