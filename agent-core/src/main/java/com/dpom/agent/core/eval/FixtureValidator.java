package com.dpom.agent.core.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Fixture 防答案泄露校验：symptom/logs 不得把 rootCauseId 直接告诉模型；stack frame 视为合法日志。
 */
public class FixtureValidator {

    private static final Pattern STACK_FRAME = Pattern.compile("at\\s+[\\w.$]+\\.[\\w$]+\\([\\w.$]+\\.java:\\d+\\)");

    /**
     * 校验 fixture。
     *
     * @param c 评测案例
     * @return 失败原因（空表示通过）
     */
    public List<String> validate(EvalCase c) {
        List<String> failures = new ArrayList<>();
        String rootCauseId = c.expected().rootCauseId();
        if (rootCauseId == null || rootCauseId.isBlank()) {
            return failures;
        }
        if (c.symptom() != null && c.symptom().contains(rootCauseId)) {
            failures.add("SYMPTOM_LEAKS_ROOT_CAUSE");
        }
        for (String line : c.logs()) {
            if (line != null && line.contains(rootCauseId) && !STACK_FRAME.matcher(line).find()) {
                failures.add("LOG_LEAKS_ROOT_CAUSE");
            }
        }
        return failures;
    }

    /**
     * 是否通过。
     *
     * @param c 评测案例
     * @return true 当无失败
     */
    public boolean isValid(EvalCase c) {
        return validate(c).isEmpty();
    }
}
