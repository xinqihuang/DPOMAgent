package com.dpom.agent.core.logevidence;

/**
 * 有界日志摄入上限：约束进入模板挖掘与 LLM 的日志规模。
 *
 * @param maxLines             最大日志行数
 * @param maxTotalBytes        最大总字节数
 * @param maxLineBytes         单行最大字节数
 * @param maxTemplates         最大模板数
 * @param maxSamplesPerTemplate 每个模板最大样本数
 * @param maxParamValues       每个模板最大参数值数
 */
public record LogIntakeLimits(int maxLines, int maxTotalBytes, int maxLineBytes, int maxTemplates,
                              int maxSamplesPerTemplate, int maxParamValues) {

    /**
     * 默认上限。
     *
     * @return 默认有界摄入限制
     */
    public static LogIntakeLimits defaults() {
        return new LogIntakeLimits(1000, 1_000_000, 8192, 100, 5, 10);
    }
}
