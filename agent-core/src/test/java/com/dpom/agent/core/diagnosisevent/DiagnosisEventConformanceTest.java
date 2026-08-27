package com.dpom.agent.core.diagnosisevent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Diagnosis Event v1 正反例一致性测试。
 */
class DiagnosisEventConformanceTest {

    private static final String ROOT = "contracts/diagnosis-event/v1/";

    private final ObjectMapper mapper = new ObjectMapper();
    private DiagnosisEventContractValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DiagnosisEventContractValidator(new Rfc8785CanonicalJsonWriter(mapper));
    }

    @Test
    void acceptsBothPositiveFixturesAndHashesExactCanonicalBytes() throws Exception {
        for (String path : new String[]{"fixtures/valid/valid-inline.json", "fixtures/valid/valid-artifact.json"}) {
            ValidatedDiagnosisEvent event = validator.validate(read(path));
            String exactHash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(event.canonicalBytes()));
            assertThat(event.canonicalSha256()).isEqualTo(exactHash).matches("[0-9a-f]{64}");
        }
    }

    @Test
    void rejectsEveryNegativeFixtureWithItsStableError() throws Exception {
        JsonNode cases = read("fixtures/invalid/cases.json").path("cases");
        assertThat(cases).hasSize(13);
        for (JsonNode fixtureCase : cases) {
            assertNegativeCase(fixtureCase);
        }
    }

    private void assertNegativeCase(JsonNode fixtureCase) throws Exception {
        JsonNode base = read("fixtures/valid/" + fixtureCase.path("base").asText());
        JsonNode mutated = base.deepCopy();
        for (JsonNode operation : fixtureCase.path("operations")) {
            apply((ObjectNode) mutated, operation);
        }
        String expected = fixtureCase.path("expectedError").asText();
        if ("idempotency-conflict".equals(fixtureCase.path("mode").asText())) {
            String originalHash = validator.validate(base).canonicalSha256();
            assertThatThrownBy(() -> validator.validateAgainstExisting(mutated, originalHash))
                    .isInstanceOf(DiagnosisEventValidationException.class).hasMessage(expected);
        } else {
            assertThatThrownBy(() -> validator.validate(mutated))
                    .isInstanceOf(DiagnosisEventValidationException.class).hasMessage(expected);
        }
    }

    private void apply(ObjectNode target, JsonNode operation) throws Exception {
        String path = operation.path("path").asText();
        switch (operation.path("op").asText()) {
            case "remove" -> remove(target, path);
            case "set" -> set(target, path, operation.path("value"));
            case "repeat" -> set(target, path,
                    mapper.getNodeFactory().textNode(operation.path("value").asText()
                            .repeat(operation.path("count").asInt())));
            case "copy" -> set(target, path, read("fixtures/valid/" + operation.path("fromFixture").asText())
                    .at(operation.path("fromPath").asText()));
            default -> throw new IllegalArgumentException("unknown fixture operation");
        }
    }

    private void remove(ObjectNode target, String pointer) {
        ObjectNode parent = (ObjectNode) target.at(parent(pointer));
        parent.remove(leaf(pointer));
    }

    private void set(ObjectNode target, String pointer, JsonNode value) {
        String parentPointer = parent(pointer);
        ObjectNode parentNode = (ObjectNode) target.at(parentPointer);
        if (parentNode.isMissingNode()) {
            parentNode = createParent(target, parentPointer);
        }
        parentNode.set(leaf(pointer), value.deepCopy());
    }

    private ObjectNode createParent(ObjectNode target, String pointer) {
        ObjectNode current = target;
        for (String part : pointer.substring(1).split("/")) {
            current = current.withObject("/" + part);
        }
        return current;
    }

    private String parent(String pointer) {
        int index = pointer.lastIndexOf('/');
        return index == 0 ? "" : pointer.substring(0, index);
    }

    private String leaf(String pointer) {
        return pointer.substring(pointer.lastIndexOf('/') + 1);
    }

    private JsonNode read(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(ROOT + path)) {
            assertThat(input).as("classpath resource %s", ROOT + path).isNotNull();
            return mapper.readTree(input);
        }
    }
}
