package com.dpom.agent.core.authority;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** 稳定、不可猜测且可重复推导的权威域标识。 */
public record AuthorityId(String value) {

    private static final Pattern VALUE = Pattern.compile("[a-z][a-z0-9-]{1,31}:[0-9a-f]{64}");

    /** 校验规范标识。 */
    public AuthorityId {
        Objects.requireNonNull(value, "value");
        if (!VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("AUTHORITY_ID_INVALID");
        }
    }

    /**
     * 从命名空间和不可变父身份推导稳定标识。
     *
     * @param namespace 标识命名空间
     * @param parts     不可变身份部分
     * @return 稳定标识
     */
    public static AuthorityId derive(String namespace, String... parts) {
        String normalizedNamespace = required(namespace, 32).toLowerCase();
        if (!normalizedNamespace.matches("[a-z][a-z0-9-]{1,31}")) {
            throw new IllegalArgumentException("AUTHORITY_NAMESPACE_INVALID");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, normalizedNamespace);
            for (String part : parts) {
                update(digest, required(part, 4096));
            }
            return new AuthorityId(normalizedNamespace + ":" + HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", e);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String required(String value, int maxLength) {
        String normalized = Objects.requireNonNull(value, "value").strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException("AUTHORITY_ID_PART_INVALID");
        }
        return normalized;
    }
}
