package com.dpom.agent.core.logevidence;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化前缀分离：从日志行提取 timestamp/level/logger，剩余作为 message 送入 Drain3。
 */
public class LogPrefixSplitter {

    private static final Pattern PREFIX = Pattern.compile(
            "^(?:(?<ts>\\[\\d{4}-\\d{2}-\\d{2}[^\\]]*\\])\\s+)?"
                    + "(?<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+"
                    + "(?:(?<logger>[\\w.$#/]+)\\s*(?:-|:)\\s*)?(?<msg>.*)$");

    /**
     * 分离一条日志行。
     *
     * @param line 日志行
     * @return 结构化日志；无法识别级别时整行作为 message，级别默认 INFO
     */
    public StructuredLog split(String line) {
        Matcher m = PREFIX.matcher(line);
        if (!m.matches()) {
            return new StructuredLog("", "INFO", "", line);
        }
        String ts = m.group("ts") == null ? "" : m.group("ts");
        String logger = m.group("logger") == null ? "" : m.group("logger");
        return new StructuredLog(ts, m.group("level"), logger, m.group("msg"));
    }
}
