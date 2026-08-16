package com.dpom.agent.core.handoff;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 证据包序列化器：把逻辑内容写成确定性 ZIP（manifest.json + checksums.json + 固定路径 section 文件 + 脱敏报告）。
 */
public class PackageSerializer {

    /** 脱敏报告固定路径。 */
    public static final String REDACTION_REPORT_PATH = "security/redaction-report.json";
    /** manifest 固定路径。 */
    public static final String MANIFEST_PATH = "manifest.json";
    /** checksums 固定路径。 */
    public static final String CHECKSUMS_PATH = "checksums.json";

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 序列化为 ZIP 字节。
     *
     * @param pkg 证据包内容
     * @return ZIP 字节
     */
    public byte[] serialize(DiagnosticEvidencePackage pkg) {
        try {
            List<String> keys = new ArrayList<>(pkg.sections().keySet());
            Collections.sort(keys);
            Map<String, byte[]> payload = new LinkedHashMap<>();
            for (String key : keys) {
                payload.put(key + ".json", mapper.writeValueAsBytes(pkg.sections().get(key)));
            }
            byte[] report = mapper.writeValueAsBytes(new TreeMap<>(pkg.redactionCounts()));
            payload.put(REDACTION_REPORT_PATH, report);

            Map<String, String> checksums = new LinkedHashMap<>();
            for (Map.Entry<String, byte[]> e : payload.entrySet()) {
                checksums.put(e.getKey(), sha256(e.getValue()));
            }
            List<PackageEntry> entries = new ArrayList<>();
            for (Map.Entry<String, byte[]> e : payload.entrySet()) {
                entries.add(new PackageEntry(e.getKey(), checksums.get(e.getKey()), e.getValue().length,
                        categoryOf(e.getKey())));
            }
            PackageManifest manifest = new PackageManifest(pkg.schemaVersion(), pkg.packageId(), pkg.service(),
                    pkg.environment(), pkg.release(), pkg.commit(), pkg.timeRange(), entries);
            byte[] manifestBytes = mapper.writeValueAsBytes(manifest);
            byte[] checksumsBytes = mapper.writeValueAsBytes(checksums);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
                put(zos, MANIFEST_PATH, manifestBytes);
                put(zos, CHECKSUMS_PATH, checksumsBytes);
                for (Map.Entry<String, byte[]> e : payload.entrySet()) {
                    put(zos, e.getKey(), e.getValue());
                }
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new HandoffException(HandoffErrorCode.PACKAGE_INVALID, "serialize failed");
        }
    }

    /**
     * 条目类别：security/redaction-report.json 归 security，其余取 section 名。
     */
    private static String categoryOf(String path) {
        if (REDACTION_REPORT_PATH.equals(path)) {
            return "security";
        }
        return path.endsWith(".json") ? path.substring(0, path.length() - ".json".length()) : path;
    }

    private void put(ZipOutputStream zos, String path, byte[] content) throws IOException {
        zos.putNextEntry(new ZipEntry(path));
        zos.write(content);
        zos.closeEntry();
    }

    /**
     * SHA-256 十六进制。
     */
    static String sha256(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
