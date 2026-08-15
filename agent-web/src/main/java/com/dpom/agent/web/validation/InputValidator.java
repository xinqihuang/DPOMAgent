package com.dpom.agent.web.validation;

import com.dpom.agent.web.dto.InvestigationSubmitRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 调查提交请求输入校验（格式 + 有界 + 安全）。
 */
public class InputValidator {

    private static final int MAX_LOGS = 1000;
    private static final int MAX_LINE_BYTES = 8192;
    private static final int MAX_TOTAL_BYTES = 1_000_000;
    private static final int SYMPTOM_MAX_CHARS = 512;
    private static final int SYMPTOM_MAX_BYTES = 1024;
    private static final int IDEMPOTENCY_MAX = 128;
    private static final long TIME_RANGE_MAX_MINUTES = 24 * 60;
    private static final Pattern TIME_RANGE = Pattern.compile("(\\d+)([mh])");
    private static final List<String> EXECUTION_MARKERS = List.of(
            "rm -rf", "sudo ", "curl ", "wget ", "nc ", "shutdown ", "reboot ", "chmod ",
            "chown ", "mkfs ", "kill -9", "http://", "https://", "ftp://", "file://",
            "/etc/", "/bin/", "/usr/", "/var/", "/tmp/", "/home/", "/root/", "/proc/", "/sys/",
            "$(", "`", "&&", "||");

    public List<String> validate(InvestigationSubmitRequest req) {
        List<String> errors = new ArrayList<>();
        if (!matches(req.serviceCode(), "[a-z0-9-]{1,128}")) errors.add("serviceCode invalid");
        if (!matches(req.environment(), "[a-z0-9-]{1,64}")) errors.add("environment invalid");
        if (!matches(req.release(), "[A-Za-z0-9._-]{1,64}")) errors.add("release invalid");
        if (!matches(req.commit(), "[0-9a-fA-F]{6,64}")) errors.add("commit invalid");
        validateSymptom(req.symptom(), errors);
        if (!validTimeRange(req.timeRange())) errors.add("timeRange invalid");
        validateIdempotencyKey(req.idempotencyKey(), errors);
        validateLogs(req.logs(), errors);
        return errors;
    }

    private void validateSymptom(String symptom, List<String> errors) {
        if (symptom == null || symptom.isBlank()) {
            errors.add("symptom required");
            return;
        }
        if (symptom.length() > SYMPTOM_MAX_CHARS) errors.add("symptom too long");
        if (symptom.getBytes(StandardCharsets.UTF_8).length > SYMPTOM_MAX_BYTES) errors.add("symptom too large");
        if (containsExecutionDirective(symptom)) errors.add("symptom invalid");
    }

    private boolean containsExecutionDirective(String symptom) {
        String lower = symptom.toLowerCase(Locale.ROOT);
        for (String marker : EXECUTION_MARKERS) {
            if (lower.contains(marker)) return true;
        }
        return false;
    }

    private boolean validTimeRange(String timeRange) {
        if (timeRange == null) return false;
        Matcher m = TIME_RANGE.matcher(timeRange);
        if (!m.matches()) return false;
        long minutes = "h".equals(m.group(2)) ? Long.parseLong(m.group(1)) * 60 : Long.parseLong(m.group(1));
        return minutes >= 1 && minutes <= TIME_RANGE_MAX_MINUTES;
    }

    private void validateIdempotencyKey(String key, List<String> errors) {
        if (key == null || key.isBlank()) return;
        if (!matches(key, "[A-Za-z0-9._-]{1," + IDEMPOTENCY_MAX + "}")) errors.add("idempotencyKey invalid");
    }

    private void validateLogs(List<String> logs, List<String> errors) {
        if (logs == null || logs.isEmpty()) {
            errors.add("logs required");
            return;
        }
        if (logs.size() > MAX_LOGS) errors.add("logs too many");
        long total = 0;
        for (String line : logs) {
            if (line == null) continue;
            if (line.getBytes(StandardCharsets.UTF_8).length > MAX_LINE_BYTES) errors.add("log line too long");
            total += line.getBytes(StandardCharsets.UTF_8).length;
        }
        if (total > MAX_TOTAL_BYTES) errors.add("logs too large");
    }

    private boolean matches(String value, String regex) {
        return value != null && value.matches(regex);
    }
}
