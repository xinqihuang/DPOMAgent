package com.dpom.agent.core.diagnosisevent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仓库内 Producer-owned 契约完整性测试。
 */
class ContractAssetIntegrityTest {

    private static final String ROOT = "contracts/";

    @Test
    void repositoryAssetsMatchRecordedSourceHashes() throws Exception {
        Map<String, String> manifest = loadManifest();

        assertThat(manifest).hasSize(39);
        for (Map.Entry<String, String> entry : manifest.entrySet()) {
            String path = entry.getKey();
            assertThat(sha256(read(path)))
                    .as("Producer-owned 契约 %s 必须与来源清单一致", path)
                    .isEqualTo(entry.getValue());
        }
    }

    private Map<String, String> loadManifest() throws IOException {
        Map<String, String> manifest = new LinkedHashMap<>();
        String content;
        try (InputStream input = resource("SHA256SUMS")) {
            content = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        for (String line : content.lines().toList()) {
            String[] parts = line.split("  ", 2);
            assertThat(parts).as("SHA256SUMS line").hasSize(2);
            manifest.put(parts[1], parts[0]);
        }
        return manifest;
    }

    private byte[] read(String path) throws IOException {
        try (InputStream input = resource(path)) {
            return input.readAllBytes();
        }
    }

    private InputStream resource(String path) {
        InputStream input = getClass().getClassLoader().getResourceAsStream(ROOT + path);
        assertThat(input).as("classpath resource %s", ROOT + path).isNotNull();
        return input;
    }

    private String sha256(byte[] content) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
