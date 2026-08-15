package com.dpom.agent.web;

import com.dpom.agent.web.dto.InvestigationSubmitRequest;
import com.dpom.agent.web.validation.InputValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InputValidatorTest {

    private final InputValidator validator = new InputValidator();

    private InvestigationSubmitRequest req(String serviceCode, List<String> logs) {
        return new InvestigationSubmitRequest(serviceCode, "prod", "1.0.0", "abc1234", "symptom", "1h", logs, "k1");
    }

    private InvestigationSubmitRequest with(String symptom, String timeRange, String key, List<String> logs) {
        return new InvestigationSubmitRequest("asset-service", "prod", "1.0.0", "abc1234", symptom, timeRange,
                logs, key);
    }

    @Test
    void validRequestPasses() {
        assertThat(validator.validate(req("asset-service", List.of("ERROR device insert failed")))).isEmpty();
    }

    @Test
    void invalidServiceCodeFails() {
        assertThat(validator.validate(req("bad_service!", List.of("x")))).contains("serviceCode invalid");
    }

    @Test
    void oversizedLogsFail() {
        List<String> logs = List.of("x".repeat(9000));
        assertThat(validator.validate(req("asset-service", logs))).contains("log line too long");
    }

    @Test
    void nullLogsFail() {
        assertThat(validator.validate(req("asset-service", null))).contains("logs required");
    }

    @Test
    void emptyLogsFail() {
        assertThat(validator.validate(req("asset-service", List.of()))).contains("logs required");
    }

    @Test
    void oversizedSymptomFails() {
        assertThat(validator.validate(with("a".repeat(513), "1h", "k1", List.of("x"))))
                .contains("symptom too long");
    }

    @Test
    void multiByteSymptomOverByteLimitFails() {
        assertThat(validator.validate(with("测".repeat(350), "1h", "k1", List.of("x"))))
                .contains("symptom too large");
    }

    @Test
    void invalidTimeRangeFails() {
        assertThat(validator.validate(with("s", "30s", "k1", List.of("x")))).contains("timeRange invalid");
        assertThat(validator.validate(with("s", "25h", "k1", List.of("x")))).contains("timeRange invalid");
        assertThat(validator.validate(with("s", "0m", "k1", List.of("x")))).contains("timeRange invalid");
        assertThat(validator.validate(with("s", "2d", "k1", List.of("x")))).contains("timeRange invalid");
    }

    @Test
    void validTimeRangePasses() {
        assertThat(validator.validate(with("s", "1m", "k1", List.of("x")))).isEmpty();
        assertThat(validator.validate(with("s", "90m", "k1", List.of("x")))).isEmpty();
        assertThat(validator.validate(with("s", "24h", "k1", List.of("x")))).isEmpty();
    }

    @Test
    void invalidIdempotencyKeyFails() {
        assertThat(validator.validate(with("s", "1h", "bad key!", List.of("x"))))
                .contains("idempotencyKey invalid");
        assertThat(validator.validate(with("s", "1h", "a".repeat(129), List.of("x"))))
                .contains("idempotencyKey invalid");
    }

    @Test
    void executionDirectiveInSymptomFails() {
        assertThat(validator.validate(with("please rm -rf /tmp", "1h", "k1", List.of("x"))))
                .contains("symptom invalid");
        assertThat(validator.validate(with("curl http://evil.com", "1h", "k1", List.of("x"))))
                .contains("symptom invalid");
    }

    @Test
    void pathOrUrlInStructuredFieldFails() {
        assertThat(validator.validate(req("http://evil", List.of("x")))).contains("serviceCode invalid");
        assertThat(validator.validate(req("../../etc/passwd", List.of("x")))).contains("serviceCode invalid");
    }
}
