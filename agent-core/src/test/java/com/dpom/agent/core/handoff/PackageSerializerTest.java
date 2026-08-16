package com.dpom.agent.core.handoff;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证据包序列化器单元测试：确定性输出与固定结构。
 */
class PackageSerializerTest {

    private final PackageSerializer serializer = new PackageSerializer();

    @Test
    void serializationIsDeterministic() {
        DiagnosticEvidencePackage pkg = samplePackage();
        byte[] first = serializer.serialize(pkg);
        byte[] second = serializer.serialize(pkg);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void containsManifestChecksumsAndSections() throws Exception {
        byte[] zip = serializer.serialize(samplePackage());
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
            var entry = zis.getNextEntry();
            while (entry != null) {
                entries.put(entry.getName(), zis.readAllBytes());
                entry = zis.getNextEntry();
            }
            assertThat(entries).containsKeys("manifest.json", "checksums.json", "logs.json", "code-context.json",
                    "security/redaction-report.json");
            assertThat(new String(entries.get("manifest.json"), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("\"packageId\":\"p1\"");
        }
    }

    private DiagnosticEvidencePackage samplePackage() {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("logs", List.of("template A count=1"));
        sections.put("code-context", List.of("CLASS_METHOD:com.example.Service.handle"));
        sections.put("contradictions", List.of("a vs b"));
        return new DiagnosticEvidencePackage(1, "p1", "svc", "env", "rel", "commit", "1h", sections, Map.of());
    }
}
