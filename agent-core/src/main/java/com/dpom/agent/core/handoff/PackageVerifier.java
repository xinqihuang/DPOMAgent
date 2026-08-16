package com.dpom.agent.core.handoff;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 证据包校验器：下载后 fail closed 校验（路径 allow-list、schema、身份、checksum、大小/条数、禁止字段）。
 */
public class PackageVerifier {

    private static final Set<String> ALLOWED_PATHS = allowedPaths();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ForbiddenContentScanner scanner = new ForbiddenContentScanner();

    /**
     * 校验证据包 ZIP 字节，返回可恢复内容；任一失败抛 HandoffException。
     *
     * @param zipBytes         下载的证据包字节
     * @param expectedService  期望服务编码
     * @param expectedRelease  期望发布版本
     * @param expectedCommit   期望提交 SHA
     * @param config           交接配置
     * @return 校验通过的内容
     */
    public RecoveredEvidencePackage verify(byte[] zipBytes, String expectedService, String expectedRelease,
                                           String expectedCommit, HandoffConfig config) {
        requireText(expectedService, "expectedService");
        requireText(expectedRelease, "expectedRelease");
        requireText(expectedCommit, "expectedCommit");
        if (zipBytes == null || zipBytes.length == 0) {
            throw new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "empty package");
        }
        if (zipBytes.length > sizeCap(config)) {
            throw new HandoffException(HandoffErrorCode.SIZE_EXCEEDED, "package too large");
        }
        Map<String, byte[]> entries = readEntries(zipBytes, config);
        byte[] manifestBytes = entries.get(PackageSerializer.MANIFEST_PATH);
        byte[] checksumsBytes = entries.get(PackageSerializer.CHECKSUMS_PATH);
        if (manifestBytes == null || checksumsBytes == null) {
            throw new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "manifest or checksums missing");
        }
        PackageManifest manifest = parseManifest(manifestBytes);
        if (manifest.schemaVersion() != config.schemaVersion()) {
            throw new HandoffException(HandoffErrorCode.SCHEMA_UNSUPPORTED, "unsupported schema version");
        }
        if (!expectedService.equals(manifest.service()) || !expectedRelease.equals(manifest.release())
                || !expectedCommit.equals(manifest.commit())) {
            throw new HandoffException(HandoffErrorCode.VERSION_MISMATCH, "service/release/commit mismatch");
        }
        Map<String, String> checksums = parseChecksums(checksumsBytes);
        for (PackageEntry entry : manifest.entries()) {
            byte[] content = entries.get(entry.path());
            if (content == null) {
                throw new HandoffException(HandoffErrorCode.CHECKSUM_MISMATCH, "entry missing: " + entry.path());
            }
            String expected = checksums.get(entry.path());
            String actual = PackageSerializer.sha256(content);
            if (!actual.equals(entry.checksum()) || !actual.equals(expected)) {
                throw new HandoffException(HandoffErrorCode.CHECKSUM_MISMATCH, "checksum mismatch: " + entry.path());
            }
        }
        return rebuild(manifest, entries, config);
    }

    /**
     * 从校验通过的条目重建内容，并做禁止字段与大小/条数上限二次校验。
     */
    private RecoveredEvidencePackage rebuild(PackageManifest manifest, Map<String, byte[]> entries,
                                             HandoffConfig config) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        long bytes = 0;
        int count = 0;
        for (PackageEntry entry : manifest.entries()) {
            if (PackageSerializer.REDACTION_REPORT_PATH.equals(entry.path())) {
                continue;
            }
            String key = entry.path().endsWith(".json") ? entry.path().substring(0, entry.path().length() - 5) : entry.path();
            List<String> values = parseStrings(entries.get(entry.path()));
            for (String v : values) {
                scanner.scan(v);
                bytes += v.getBytes(StandardCharsets.UTF_8).length;
                count++;
            }
            sections.put(key, values);
        }
        if (count > config.maxPackageEntries()) {
            throw new HandoffException(HandoffErrorCode.ENTRIES_EXCEEDED, "entries exceed limit");
        }
        if (bytes > config.maxPackageBytes()) {
            throw new HandoffException(HandoffErrorCode.SIZE_EXCEEDED, "package bytes exceed limit");
        }
        return new RecoveredEvidencePackage(manifest.schemaVersion(), manifest.packageId(), manifest.service(),
                manifest.environment(), manifest.release(), manifest.commit(), manifest.timeRange(), sections);
    }

    /**
     * 有界读取 ZIP 全部条目，校验路径 allow-list，返回 path -> bytes。
     */
    private Map<String, byte[]> readEntries(byte[] zipBytes, HandoffConfig config) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        long total = 0;
        long cap = sizeCap(config);
        long perEntryCap = (long) config.maxPackageBytes() + 8192L;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String path = entry.getName();
                if (!ALLOWED_PATHS.contains(path)) {
                    throw new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "path not allowed: " + path);
                }
                byte[] content = readBounded(zis, perEntryCap);
                total += content.length;
                if (total > cap) {
                    throw new HandoffException(HandoffErrorCode.SIZE_EXCEEDED, "package too large");
                }
                out.put(path, content);
            }
        } catch (IOException e) {
            throw new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "zip read failed");
        }
        return out;
    }

    private byte[] readBounded(InputStream in, long cap) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > cap) {
                throw new HandoffException(HandoffErrorCode.SIZE_EXCEEDED, "entry too large");
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private PackageManifest parseManifest(byte[] content) {
        try {
            return mapper.readValue(content, PackageManifest.class);
        } catch (IOException e) {
            throw new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "manifest invalid");
        }
    }

    private Map<String, String> parseChecksums(byte[] content) {
        try {
            return mapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
        } catch (IOException e) {
            throw new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "checksums invalid");
        }
    }

    private List<String> parseStrings(byte[] content) {
        try {
            return mapper.readValue(content, new TypeReference<List<String>>() {
            });
        } catch (IOException e) {
            throw new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "section invalid");
        }
    }

    private long sizeCap(HandoffConfig config) {
        return (long) config.maxPackageBytes() * 2L;
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new HandoffException(HandoffErrorCode.INVALID_ARGUMENT, field + " required");
        }
    }

    private static Set<String> allowedPaths() {
        Set<String> paths = new HashSet<>();
        paths.add(PackageSerializer.MANIFEST_PATH);
        paths.add(PackageSerializer.CHECKSUMS_PATH);
        paths.add(PackageSerializer.REDACTION_REPORT_PATH);
        for (String section : DiagnosticEvidencePackageBuilder.ALLOWED_SECTIONS) {
            paths.add(section + ".json");
        }
        return Set.copyOf(paths);
    }
}
