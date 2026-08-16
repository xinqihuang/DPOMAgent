package com.dpom.agent.core.handoff;

import java.util.regex.Pattern;

/**
 * 禁止内容扫描器：第二层防御，在 LogRedactor 脱敏之后拒绝源码形态标记与凭据形态键值。
 */
public final class ForbiddenContentScanner {

    /** 源码形态标记（行首 package/import/class/interface/enum/def/func/fn/#include/using namespace）。 */
    private static final Pattern SOURCE_MARKER = Pattern.compile(
            "(?m)^\\s*(package\\s+[A-Za-z_]|import\\s+[A-Za-z_]|public\\s+(class|interface|enum)\\b|"
                    + "private\\s+(class|interface|enum)\\b|protected\\s+(class|interface|enum)\\b|"
                    + "class\\s+[A-Za-z_$][\\w$]*\\s*\\{|interface\\s+[A-Za-z_$][\\w$]*\\s*\\{|"
                    + "enum\\s+[A-Za-z_$][\\w$]*\\s*\\{|def\\s+\\w+\\s*\\(|func\\s+\\w+\\s*\\(|"
                    + "fn\\s+\\w+\\s*\\(|#include\\b|using\\s+namespace\\b)");

    /** 凭据形态键值（accessKey/secretKey/authorization/password/token/cookie/privateKey 等）。 */
    private static final Pattern CREDENTIAL_KEY = Pattern.compile(
            "(?i)(access[_-]?key|secret[_-]?key|secret[_-]?access[_-]?key|authorization|password|token|"
                    + "cookie|api[_-]?key|private[_-]?key)\\s*[:=]\\s*([^\\s,;\"'}]+)");

    /** AK/SK 形态赋值（行首 AK= / SK=）。 */
    private static final Pattern AK_SK = Pattern.compile("(?m)^\\s*(?i)(AK|SK)\\s*=\\s*\\S+");

    /**
     * 扫描文本，发现禁止内容即抛异常。
     *
     * @param text 待扫描文本（已脱敏）
     * @throws HandoffException 发现源码或凭据形态内容
     */
    public void scan(String text) {
        if (containsForbidden(text)) {
            throw new HandoffException(HandoffErrorCode.FORBIDDEN_CONTENT, "forbidden content detected");
        }
    }

    /**
     * 是否包含禁止内容。
     *
     * @param text 待扫描文本
     * @return true 表示包含源码形态或凭据形态内容
     */
    public boolean containsForbidden(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (SOURCE_MARKER.matcher(text).find()) {
            return true;
        }
        if (AK_SK.matcher(text).find()) {
            return true;
        }
        var m = CREDENTIAL_KEY.matcher(text);
        while (m.find()) {
            String value = m.group(2);
            if (value != null && !value.startsWith("h:") && !value.startsWith("[REDACTED")) {
                return true;
            }
        }
        return false;
    }
}
