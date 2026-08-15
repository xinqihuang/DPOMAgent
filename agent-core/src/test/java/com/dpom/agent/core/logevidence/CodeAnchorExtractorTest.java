package com.dpom.agent.core.logevidence;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T104 代码锚点确定性提取验收。
 */
class CodeAnchorExtractorTest {

    private final CodeAnchorExtractor extractor = new CodeAnchorExtractor();

    private LogEvidence evidence(String template, String... samples) {
        LogTemplateSummary summary = new LogTemplateSummary(1, template, samples.length, null, null,
                Map.of("ERROR", samples.length), Arrays.asList(samples), new ParameterDistribution(Map.of()), false);
        return new LogEvidence("ev-1", summary, "s", "prod", "1.0.0", "c", "1h", null, "drain3-0.9",
                new EvidenceProvenance("drain3", "c", null, null, "v1", null));
    }

    /**
     * 提取异常、栈帧、类方法、HTTP 路径与 mapper id，且每个锚点带来源/置信度/规则版本。
     */
    @Test
    void extractsExceptionStackFrameClassHttpMapper() {
        LogEvidence e = evidence("request failed",
                "java.lang.IllegalStateException: boom",
                "    at com.example.AssetRepository.insert(AssetRepository.java:42)",
                "com.example.AssetService.create(AssetService.java:35)",
                "POST /api/v1/devices",
                "com.example.AssetMapper.insert");

        List<CodeAnchor> anchors = extractor.extract(e);

        assertThat(anchors).extracting(CodeAnchor::type).contains(
                "EXCEPTION", "STACK_FRAME", "CLASS_METHOD", "HTTP_PATH", "MAPPER_ID", "LOG_CONSTANT");
        assertThat(anchors).allSatisfy(a -> {
            assertThat(a.sourceEvidenceId()).isEqualTo("ev-1");
            assertThat(a.ruleVersion()).isEqualTo("v1");
            assertThat(a.confidence()).isGreaterThan(0.0);
        });
        assertThat(anchors).anyMatch(a -> a.type().equals("EXCEPTION")
                && a.value().equals("java.lang.IllegalStateException"));
        assertThat(anchors).anyMatch(a -> a.type().equals("MAPPER_ID") && a.value().contains("AssetMapper"));
        assertThat(anchors).anyMatch(a -> a.type().equals("HTTP_PATH") && a.value().equals("POST /api/v1/devices"));
    }

    /**
     * 无代码引用时只产生日志常量锚点。
     */
    @Test
    void emptyAnchorProducesOnlyLogConstant() {
        LogEvidence e = evidence("plain message without code");
        assertThat(extractor.extract(e)).extracting(CodeAnchor::type).containsExactly("LOG_CONSTANT");
    }

    /**
     * 重复锚点去重。
     */
    @Test
    void dedupesRepeatedAnchors() {
        LogEvidence e = evidence("boom", "java.lang.IllegalStateException", "java.lang.IllegalStateException");
        long exCount = extractor.extract(e).stream().filter(a -> a.type().equals("EXCEPTION")).count();
        assertThat(exCount).isEqualTo(1);
    }
}
