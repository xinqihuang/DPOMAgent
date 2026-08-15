package com.dpom.agent.core.logevidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏：对 Authorization/token/password/secret/email/IP 等做不可逆替换，保留稳定 hash 用于同值关联。
 */
public class LogRedactor {

    private static final Pattern SECRET = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*(?:bearer\\s+)?|password\\s*[:=]\\s*|token\\s*[:=]\\s*"
                    + "|secret\\s*[:=]\\s*|api[_-]?key\\s*[:=]\\s*)(\\S+)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    private static final Pattern IP = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b");

    /**
     * 脱敏文本：敏感键值替换为稳定 hash，邮箱/IP 替换为占位符。
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public String redact(String text) {
        String out = SECRET.matcher(text).replaceAll(mr -> mr.group(1) + stableHash(mr.group(2)));
        out = EMAIL.matcher(out).replaceAll("[REDACTED:email]");
        out = IP.matcher(out).replaceAll("[REDACTED:ip]");
        return out;
    }

    /**
     * 生成不可逆稳定 hash（SHA-256 前 4 字节）。
     *
     * @param value 原始值
     * @return h: 前缀的稳定 hash
     */
    public String stableHash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("h:");
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "[REDACTED]";
        }
    }
}
