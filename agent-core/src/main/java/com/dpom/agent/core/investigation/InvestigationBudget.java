package com.dpom.agent.core.investigation;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 调查预算：跟踪步数/工具调用/时长/无进展轮数，达到任一上限即视为超限。
 */
public final class InvestigationBudget {

    private final int maxSteps;
    private final int maxToolCalls;
    private final int maxDurationSeconds;
    private final int maxNoProgressRounds;
    private final LocalDateTime startedAt;

    private int stepsUsed;
    private int toolCallsUsed;
    private int noProgressRounds;

    /**
     * 构造预算。
     *
     * @param maxSteps           最大步数
     * @param maxToolCalls       最大工具调用数
     * @param maxDurationSeconds 最大时长（秒）
     * @param maxNoProgressRounds 最大无进展轮数
     * @param startedAt          开始时间
     */
    public InvestigationBudget(int maxSteps, int maxToolCalls, int maxDurationSeconds,
                               int maxNoProgressRounds, LocalDateTime startedAt) {
        this.maxSteps = maxSteps;
        this.maxToolCalls = maxToolCalls;
        this.maxDurationSeconds = maxDurationSeconds;
        this.maxNoProgressRounds = maxNoProgressRounds;
        this.startedAt = startedAt;
    }

    /**
     * 从调查记录构造预算。
     *
     * @param investigation 调查
     * @param startedAt     开始时间
     * @return 预算
     */
    public static InvestigationBudget from(Investigation investigation, LocalDateTime startedAt) {
        return new InvestigationBudget(investigation.maxSteps(), investigation.maxToolCalls(),
                investigation.maxDurationSeconds(), investigation.maxNoProgressRounds(), startedAt);
    }

    /**
     * 记录一步。
     */
    public void recordStep() {
        stepsUsed++;
    }

    /**
     * 记录一次工具调用。
     */
    public void recordToolCall() {
        toolCallsUsed++;
    }

    /**
     * 记录有进展（清零无进展轮数）。
     */
    public void recordProgress() {
        noProgressRounds = 0;
    }

    /**
     * 记录无进展一轮。
     */
    public void recordNoProgress() {
        noProgressRounds++;
    }

    /**
     * 判断是否超限。
     *
     * @param now 当前时间
     * @return 是否超限
     */
    public boolean isExceeded(LocalDateTime now) {
        if (stepsUsed >= maxSteps) {
            return true;
        }
        if (toolCallsUsed >= maxToolCalls) {
            return true;
        }
        if (noProgressRounds >= maxNoProgressRounds) {
            return true;
        }
        long elapsedSeconds = Duration.between(startedAt, now).getSeconds();
        return elapsedSeconds >= maxDurationSeconds;
    }

    /**
     * 已用步数。
     *
     * @return 已用步数
     */
    public int stepsUsed() {
        return stepsUsed;
    }

    /**
     * 已用工具调用数。
     *
     * @return 已用工具调用数
     */
    public int toolCallsUsed() {
        return toolCallsUsed;
    }
}
