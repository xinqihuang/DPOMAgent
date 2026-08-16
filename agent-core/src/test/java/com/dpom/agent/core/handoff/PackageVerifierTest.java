package com.dpom.agent.core.handoff;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * 证据包校验器单元测试：schema、身份、checksum、大小/条数、禁止字段，fail closed。
 */
class PackageVerifierTest {

    private final PackageSerializer serializer = new PackageSerializer();
    private final PackageVerifier verifier = new PackageVerifier();
    private final HandoffConfig config = HandoffConfig.defaults();

    @Test
    void validPackageRoundTrips() {
        byte[] zip = serializer.serialize(samplePackage());
        RecoveredEvidencePackage pkg = verifier.verify(zip, "svc", "rel", "commit", config);
        assertThat(pkg.packageId()).isEqualTo("p1");
        assertThat(pkg.sections().get("logs")).containsExactly("template A count=1");
    }

    @Test
    void rejectsUnsupportedSchema() {
        byte[] zip = serializer.serialize(samplePackageWithSchema(2));
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> verifier.verify(zip, "svc", "rel", "commit", config))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.SCHEMA_UNSUPPORTED);
    }

    @Test
    void rejectsServiceMismatch() {
        byte[] zip = serializer.serialize(samplePackage());
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> verifier.verify(zip, "other", "rel", "commit", config))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.VERSION_MISMATCH);
    }

    @Test
    void rejectsReleaseCommitMismatch() {
        byte[] zip = serializer.serialize(samplePackage());
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> verifier.verify(zip, "svc", "other-rel", "commit", config))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.VERSION_MISMATCH);
    }

    @Test
    void rejectsChecksumMismatch() throws IOException {
        byte[] zip = serializer.serialize(samplePackage());
        byte[] tampered = rewriteEntry(zip, "logs.json", "[\"tampered\"]".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> verifier.verify(tampered, "svc", "rel", "commit", config))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.CHECKSUM_MISMATCH);
    }

    @Test
    void rejectsForbiddenContentInPackage() {
        DiagnosticEvidencePackage pkg = new DiagnosticEvidencePackage(1, "p1", "svc", "env", "rel", "commit", "1h",
                Map.of("logs", List.of("package com.evil;")), Map.of());
        byte[] zip = serializer.serialize(pkg);
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> verifier.verify(zip, "svc", "rel", "commit", config))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.FORBIDDEN_CONTENT);
    }

    @Test
    void rejectsEmptyPackage() {
        assertThatExceptionOfType(HandoffException.class)
                .isThrownBy(() -> verifier.verify(new byte[0], "svc", "rel", "commit", config))
                .extracting(HandoffException::code)
                .isEqualTo(HandoffErrorCode.PACKAGE_INVALID);
    }

    private DiagnosticEvidencePackage samplePackage() {
        return samplePackageWithSchema(1);
    }

    private DiagnosticEvidencePackage samplePackageWithSchema(int schema) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("logs", List.of("template A count=1"));
        sections.put("contradictions", List.of("a vs b"));
        return new DiagnosticEvidencePackage(schema, "p1", "svc", "env", "rel", "commit", "1h", sections, Map.of());
    }

    private byte[] rewriteEntry(byte[] zip, String name, byte[] replacement) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip));
                ZipOutputStream zos = new ZipOutputStream(bos)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().equals(name)) {
                    zos.write(replacement);
                } else {
                    zos.write(zis.readAllBytes());
                }
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
