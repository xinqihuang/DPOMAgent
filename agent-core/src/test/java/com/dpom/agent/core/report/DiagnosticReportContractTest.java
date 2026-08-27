package com.dpom.agent.core.report;

import com.dpom.agent.core.diagnosisevent.Rfc8785CanonicalJsonWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosticReportContractTest {
    private static final String ROOT = "contracts/diagnostic-report/v1/";
    private static final List<String> VALID = List.of("diagnosis-complete.json", "diagnosis-incomplete.json",
            "evaluated-pass.json", "evaluated-fail.json", "evaluated-incomplete.json");
    private final ObjectMapper json = new ObjectMapper();
    private final DiagnosticReportValidator validator = new DiagnosticReportValidator(json);
    private final JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(read("diagnostic-report.schema.json"));

    @Test
    void everyProfileFixtureIsStructurallyAndSemanticallyValid() {
        for (String name : VALID) {
            JsonNode report = fixture(name);
            assertThat(schema.validate(report)).as(name).isEmpty();
            validator.validate(report);
        }
    }

    @Test
    void conclusionsRequireDispositionButOtherClaimsRejectIt() {
        ObjectNode report = fixture("diagnosis-complete.json");
        ObjectNode conclusion = (ObjectNode) report.path("conclusions").path(0);
        conclusion.remove("disposition");
        assertThat(schema.validate(report)).isNotEmpty();

        report = fixture("diagnosis-complete.json");
        ((ObjectNode) report.path("observations").path(0)).put("disposition", "CONFIRMED");
        assertThat(schema.validate(report)).isNotEmpty();
    }

    @Test
    void negativeFixturesFailSchemaOrSemanticValidation() {
        for (JsonNode fixtureCase : read("fixtures/invalid/cases.json").path("cases")) {
            JsonNode invalid = mutate(fixtureCase);
            boolean schemaFailed = !schema.validate(invalid).isEmpty();
            boolean semanticFailed = catchesSemanticFailure(invalid);
            assertThat(schemaFailed || semanticFailed).as(fixtureCase.path("name").asText()).isTrue();
        }
    }

    @Test
    void canonicalVectorsAndFixtureDigestsAreStable() throws Exception {
        JsonNode vectors = read("canonical-vectors.json").path("vectors");
        Rfc8785CanonicalJsonWriter canonical = new Rfc8785CanonicalJsonWriter(json);
        JsonNode objectVector = vectors.path(0);
        byte[] bytes = canonical.write(objectVector.path("input"));
        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .isEqualTo(objectVector.path("canonicalUtf8").asText());
        assertThat(sha256(bytes)).isEqualTo(objectVector.path("sha256").asText());
        assertThat(validator.digest(fixture("diagnosis-complete.json")))
                .isEqualTo(vectors.path(1).path("sha256").asText());
    }

    @Test
    void alarmGoldenRevisionSeparatesEdenAndCodeCacheAndKeepsRecoveryLineage() {
        JsonNode alert = read("fixtures/golden/alarm-16557989-alert.json");
        JsonNode recovered = read("fixtures/golden/alarm-16557989-recovered.json");
        assertThat(schema.validate(alert)).isEmpty();
        assertThat(schema.validate(recovered)).isEmpty();
        validator.validate(alert);
        validator.validate(recovered);
        validator.validateRevisionChain(List.of(alert, recovered));
        assertThat(recovered.path("supersedesReportId").asText()).isEqualTo("REPORT-APM-16557989-R1");
        assertThat(recovered.path("timeline").toString()).contains("RECOVER");
        assertThat(recovered.path("observations").toString()).contains("Par Eden Space", "Code Cache");
        assertThat(recovered.path("extensions").toString()).contains("16557989", "limitation");
    }

    @Test
    void revisionChainRejectsCyclesAndNonIncreasingRevisions() {
        ObjectNode first = fixture("diagnosis-complete.json");
        ObjectNode second = fixture("diagnosis-incomplete.json");
        second.put("revision", 1);
        second.put("supersedesReportId", first.path("reportId").asText());
        second.putArray("changeReasons").add("CORRECTION");
        second.put("reportDigest", validator.digest(second));
        assertThatThrownBy(() -> validator.validateRevisionChain(List.of(first, second)))
                .hasMessage("REPORT_REVISION_NOT_INCREASING");
    }

    private ObjectNode mutate(JsonNode fixtureCase) {
        ObjectNode value = fixture(fixtureCase.path("base").asText());
        String path = fixtureCase.path("path").asText();
        JsonNode parent = value.at(path.substring(0, path.lastIndexOf('/')));
        String field = path.substring(path.lastIndexOf('/') + 1);
        switch (fixtureCase.path("operation").asText()) {
            case "remove" -> remove(parent, field);
            case "replace" -> replace(parent, field, fixtureCase.path("value"));
            case "repeat" -> ((ObjectNode) parent).put(field,
                    fixtureCase.path("value").asText().repeat(fixtureCase.path("count").asInt()));
            default -> throw new IllegalArgumentException("UNKNOWN_REPORT_MUTATION");
        }
        return value;
    }

    private void remove(JsonNode parent, String field) {
        if (parent.isArray()) {
            ((ArrayNode) parent).remove(Integer.parseInt(field));
        } else {
            ((ObjectNode) parent).remove(field);
        }
    }

    private void replace(JsonNode parent, String field, JsonNode value) {
        if (parent.isArray()) {
            ((ArrayNode) parent).set(Integer.parseInt(field), value);
        } else {
            ((ObjectNode) parent).set(field, value);
        }
    }

    private boolean catchesSemanticFailure(JsonNode report) {
        try {
            validator.validate(report);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private ObjectNode fixture(String name) {
        return (ObjectNode) read("fixtures/valid/" + name);
    }

    private JsonNode read(String relative) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(ROOT + relative)) {
            if (input == null) {
                throw new IllegalStateException("REPORT_CONTRACT_RESOURCE_MISSING: " + relative);
            }
            return json.readTree(input);
        } catch (Exception exception) {
            throw new IllegalStateException("REPORT_CONTRACT_READ_FAILED: " + relative, exception);
        }
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
