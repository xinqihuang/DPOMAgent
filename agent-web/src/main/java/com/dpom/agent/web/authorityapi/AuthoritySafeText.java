package com.dpom.agent.web.authorityapi;

import java.util.regex.Pattern;

/** 响应边界的保守凭据文本遮蔽。 */
final class AuthoritySafeText {

    private static final Pattern BEARER = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*bearer\\s+)[A-Za-z0-9._~+/-]{4,}");
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)(password|passwd|secret|api[-_ ]?key|access[-_ ]?key|token)\\s*[:=]\\s*[^\\s,;]+"
    );

    private AuthoritySafeText() {
    }

    static RedactedText redact(String value) {
        String source = value == null ? "" : value;
        String redacted = BEARER.matcher(source).replaceAll("$1[REDACTED]");
        redacted = NAMED_SECRET.matcher(redacted).replaceAll("$1=[REDACTED]");
        return new RedactedText(redacted, !source.equals(redacted));
    }

    record RedactedText(String value, boolean changed) {
    }
}

