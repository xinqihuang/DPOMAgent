package com.dpom.agent.core.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.erdtman.jcs.JsonCanonicalizer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProducerContractConformanceTest {

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "accesskey", "secretkey", "password", "authorization", "cookie", "token",
            "bucket", "objectkey", "topic", "partition", "offset", "consumergroup",
            "rawmodeloutput", "sdktype", "exception");

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path contracts = locateContracts();

    @Test
    void acceptsAllPositiveFixturesAndMatchesCanonicalManifest() throws Exception {
        Path event = contracts.resolve("diagnosis-event/v2");
        JsonSchema eventSchema = schema(event.resolve("diagnosis-event.schema.json"));
        JsonNode manifest = read(event.resolve("fixtures/manifest.json"));
        for (JsonNode item : manifest.path("fixtures")) {
            JsonNode fixture = read(event.resolve("fixtures/valid").resolve(item.path("file").asText()));
            assertThat(eventSchema.validate(fixture)).isEmpty();
            byte[] canonical = canonical(fixture);
            assertThat(canonical).hasSize(item.path("canonicalByteSize").asInt());
            assertThat(sha256(canonical)).isEqualTo(item.path("canonicalSha256").asText());
        }
        assertPositiveCorpus(contracts.resolve("evidence-manifest/v1"), "evidence-manifest.schema.json");
        assertPositiveCorpus(contracts.resolve("diagnosis-progress/v1"), "diagnosis-progress.schema.json");
    }

    @Test
    void rejectsEveryNegativeFixtureWithDeclaredStableReason() throws Exception {
        assertNegativeCorpus(contracts.resolve("diagnosis-event/v2"), "diagnosis-event.schema.json", "event");
        assertNegativeCorpus(contracts.resolve("evidence-manifest/v1"), "evidence-manifest.schema.json", "evidence");
        assertNegativeCorpus(contracts.resolve("diagnosis-progress/v1"), "diagnosis-progress.schema.json", "progress");
    }

    @Test
    void sourceManifestPinsAuthorityAndTransportNeutralCanonicalization() throws Exception {
        JsonNode manifest = read(contracts.resolve("diagnosis-event/v2/source-manifest.json"));
        assertThat(manifest.path("canonicalProducer").asText()).isEqualTo("DPOMAgent");
        assertThat(manifest.path("canonicalization").asText()).isEqualTo("RFC8785");
        assertThat(manifest.path("transportMetadataExcluded")).extracting(JsonNode::asText)
                .contains("httpHeaders", "kafkaHeaders", "partition", "offset");
    }

    @Test
    void kafkaAndHttpEnvelopesHaveIdenticalCanonicalOutcome() throws Exception {
        JsonNode value = read(contracts.resolve("diagnosis-event/v2/fixtures/valid/terminal-inline.json"));
        TransportEnvelope http = new TransportEnvelope("HTTP", value, Map.of(
                "signature", "fixture-signature", "requestTimestamp", "1787625000", "retryAttempt", "1"));
        TransportEnvelope kafka = new TransportEnvelope("KAFKA", value, Map.of(
                "topic", "dpom.diagnosis-event.v2", "partition", "3", "offset", "42"));

        assertThat(normalize(http)).isEqualTo(normalize(kafka));
        assertThat(new String(canonical(value), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("fixture-signature", "requestTimestamp", "topic", "partition", "offset");
    }

    private NormalizedOutcome normalize(TransportEnvelope envelope) throws Exception {
        JsonNode value = envelope.canonicalValue();
        String identity = value.path("eventId").asText() + ":" + value.path("idempotencyKey").asText();
        JsonNode authority = value.path("sourceAuthority");
        return new NormalizedOutcome(identity, sha256(canonical(value)), value.path("investigationId").asText(),
                value.path("aggregateSequence").asLong(), authority.path("authorityEpoch").asText(), "ACCEPTABLE");
    }

    private void assertPositiveCorpus(Path folder, String schemaFile) throws Exception {
        JsonSchema contract = schema(folder.resolve(schemaFile));
        try (var paths = Files.list(folder.resolve("fixtures/valid"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".json")).toList()) {
                assertThat(contract.validate(read(path))).as(path.toString()).isEmpty();
            }
        }
    }

    private void assertNegativeCorpus(Path folder, String schemaFile, String kind) throws Exception {
        JsonSchema contract = schema(folder.resolve(schemaFile));
        JsonNode cases = read(folder.resolve("fixtures/invalid/cases.json")).path("cases");
        for (JsonNode fixtureCase : cases) {
            JsonNode base = read(folder.resolve("fixtures/valid").resolve(fixtureCase.path("base").asText()));
            ObjectNode mutated = base.deepCopy();
            for (JsonNode operation : fixtureCase.path("operations")) {
                apply(mutated, operation);
            }
            String actual = classify(contract, mutated, fixtureCase.path("mode").asText(null), kind);
            assertThat(actual).as(fixtureCase.path("name").asText())
                    .isEqualTo(fixtureCase.path("expectedError").asText());
        }
    }

    private String classify(JsonSchema schema, JsonNode value, String mode, String kind) throws Exception {
        int size = canonical(value).length;
        if (("event".equals(kind) && (size > 65536 || inlineSize(value) > 16384))
                || ("evidence".equals(kind) && evidenceTooLarge(value, size))
                || ("progress".equals(kind) && size > 8192)) {
            return "PAYLOAD_TOO_LARGE";
        }
        if ("event".equals(kind) && !value.path("schemaVersion").asText().startsWith("2.")) {
            return "UNSUPPORTED_SCHEMA";
        }
        if (unsafe(value, kind)) {
            return "SECURITY_BOUNDARY_VIOLATION";
        }
        String stateful = statefulError(mode);
        if (stateful != null) {
            return stateful;
        }
        if ("evidence".equals(kind) && evidenceIntegrityInvalid(value)) {
            return "ARTIFACT_INTEGRITY_FAILED";
        }
        return schema.validate(value).isEmpty() ? null : "CONTRACT_VALIDATION_FAILED";
    }

    private String statefulError(String mode) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case "digest-mismatch" -> "CANONICAL_DIGEST_MISMATCH";
            case "idempotency-conflict" -> "IDEMPOTENCY_CONFLICT";
            case "sequence-gap" -> "SEQUENCE_GAP";
            case "sequence-regression" -> "SEQUENCE_REGRESSION";
            case "authority-conflict" -> "AUTHORITY_CONFLICT";
            case "artifact-integrity" -> "ARTIFACT_INTEGRITY_FAILED";
            default -> null;
        };
    }

    private boolean unsafe(JsonNode value, String kind) {
        if (value.isObject()) {
            var fields = value.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (FORBIDDEN_KEYS.contains(field.getKey().toLowerCase()) || unsafe(field.getValue(), kind)) {
                    return true;
                }
            }
            if ("evidence".equals(kind) && (value.has("content") || value.path("contentIncluded").asBoolean())) {
                return true;
            }
            if ("progress".equals(kind) && (value.has("evidence") || value.has("prompt"))) {
                return true;
            }
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (unsafe(item, kind)) {
                    return true;
                }
            }
        }
        if (value.isTextual()) {
            String text = value.asText();
            return text.contains("\\") || text.contains("../")
                    || text.toLowerCase().contains("com.huaweicloud.sdk");
        }
        return false;
    }

    private boolean evidenceTooLarge(JsonNode value, int canonicalSize) {
        if (canonicalSize > 1048576 || value.path("totalByteSize").asLong() > 52428800) {
            return true;
        }
        for (JsonNode entry : value.path("entries")) {
            if (entry.path("byteSize").asLong() > 10485760) {
                return true;
            }
        }
        return false;
    }

    private boolean evidenceIntegrityInvalid(JsonNode value) {
        long total = 0;
        for (JsonNode entry : value.path("entries")) {
            if (entry.path("sha256").asText().length() != 64) {
                return true;
            }
            total += entry.path("byteSize").asLong();
        }
        return total != value.path("totalByteSize").asLong();
    }

    private int inlineSize(JsonNode value) throws Exception {
        return value.has("inlinePayload") ? canonical(value.path("inlinePayload")).length : 0;
    }

    private void apply(ObjectNode target, JsonNode operation) {
        String pointer = operation.path("path").asText();
        JsonNode parent = target.at(parent(pointer));
        String leaf = leaf(pointer);
        switch (operation.path("op").asText()) {
            case "remove" -> remove(parent, leaf);
            case "set" -> set(parent, leaf, operation.path("value").deepCopy());
            case "repeat" -> set(parent, leaf, mapper.getNodeFactory().textNode(
                    operation.path("value").asText().repeat(operation.path("count").asInt())));
            default -> throw new IllegalArgumentException("unknown fixture operation");
        }
    }

    private void remove(JsonNode parent, String leaf) {
        if (parent.isArray()) {
            ((ArrayNode) parent).remove(Integer.parseInt(leaf));
        } else {
            ((ObjectNode) parent).remove(leaf);
        }
    }

    private void set(JsonNode parent, String leaf, JsonNode value) {
        if (parent.isArray()) {
            ((ArrayNode) parent).set(Integer.parseInt(leaf), value);
        } else {
            ((ObjectNode) parent).set(leaf, value);
        }
    }

    private String parent(String pointer) {
        int index = pointer.lastIndexOf('/');
        return index == 0 ? "" : pointer.substring(0, index);
    }

    private String leaf(String pointer) {
        return pointer.substring(pointer.lastIndexOf('/') + 1);
    }

    private JsonSchema schema(Path path) throws IOException {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(read(path));
    }

    private JsonNode read(Path path) throws IOException {
        return mapper.readTree(Files.readAllBytes(path));
    }

    private byte[] canonical(JsonNode value) throws IOException {
        return new JsonCanonicalizer(mapper.writeValueAsBytes(value)).getEncodedUTF8();
    }

    private String sha256(byte[] value) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private Path locateContracts() {
        Path cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("contracts");
            if (Files.isDirectory(candidate.resolve("diagnosis-event/v2"))) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("repository contracts directory not found");
    }

    private record TransportEnvelope(String transport, JsonNode canonicalValue, Map<String, String> metadata) { }

    private record NormalizedOutcome(String identity, String digest, String investigationId, long sequence,
                                     String authorityEpoch, String outcome) { }
}
