package com.dpom.agent.core.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixture 防答案泄露校验验收。
 */
class FixtureValidatorTest {

    private final FixtureValidator validator = new FixtureValidator();

    private static EvalCase caseWith(String symptom, List<String> logs) {
        EvalExpected expected = new EvalExpected("AssetRepository.insert",
                List.of("AssetRepository.insert", "AssetService.create"), List.of("LOG", "SOURCE"), List.of());
        return new EvalCase("E01", "asset-service", "prod", "1.0.0", "e01abc", symptom, logs, expected);
    }

    @Test
    void symptomLeakingRootCauseFails() {
        EvalCase c = caseWith("device rollback, root cause is AssetRepository.insert",
                List.of("device 1001 insert failed"));
        assertThat(validator.validate(c)).contains("SYMPTOM_LEAKS_ROOT_CAUSE");
    }

    @Test
    void logInjectingAnswerFails() {
        EvalCase c = caseWith("device rollback",
                List.of("device 1001 insert failed", "answer: AssetRepository.insert"));
        assertThat(validator.validate(c)).contains("LOG_LEAKS_ROOT_CAUSE");
    }

    @Test
    void stackFrameIsAllowed() {
        EvalCase c = caseWith("device rollback",
                List.of("device 1001 insert failed",
                        "    at com.example.asset.AssetRepository.insert(AssetRepository.java:42)"));
        assertThat(validator.validate(c)).isEmpty();
    }

    @Test
    void validFixturePasses() {
        EvalCase c = caseWith("device create transaction rollback",
                List.of("device 1001 insert failed", "java.lang.IllegalStateException: insert failed",
                        "    at com.example.asset.AssetRepository.insert(AssetRepository.java:42)"));
        assertThat(validator.isValid(c)).isTrue();
    }
}
