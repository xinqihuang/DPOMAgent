package com.dpom.agent.core.stacktrace;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 异常堆栈解析器：提取应用栈帧，过滤 JDK/框架帧。
 */
@Component
public class StacktraceParser {

    private static final Pattern FRAME_PATTERN =
            Pattern.compile("^\\s*at\\s+([\\w.$]+)\\.([\\w$<>]+)\\((.*)\\)$");

    private static final Pattern LOCATION_PATTERN = Pattern.compile("^(.*\\.java):(\\d+)$");

    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.", "org.springframework.", "org.apache.",
            "org.junit.", "org.mockito.", "net.bytebuddy.", "com.intellij.", "io.");

    /**
     * 解析堆栈，返回应用栈帧（自顶向下）。
     *
     * @param stacktrace 堆栈文本
     * @return 应用栈帧列表
     */
    public List<StackFrame> parse(String stacktrace) {
        List<StackFrame> frames = new ArrayList<>();
        for (String line : stacktrace.split("\\R")) {
            Matcher matcher = FRAME_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String className = matcher.group(1);
            if (isExcluded(className)) {
                continue;
            }
            String methodName = matcher.group(2);
            String location = matcher.group(3);
            Matcher locationMatcher = LOCATION_PATTERN.matcher(location);
            String fileName = locationMatcher.matches() ? locationMatcher.group(1) : location;
            Integer lineNumber = locationMatcher.matches() ? Integer.parseInt(locationMatcher.group(2)) : null;
            frames.add(new StackFrame(className, methodName, fileName, lineNumber));
        }
        return frames;
    }

    /**
     * 判断是否为框架/JDK 帧。
     */
    private boolean isExcluded(String className) {
        for (String prefix : EXCLUDED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
