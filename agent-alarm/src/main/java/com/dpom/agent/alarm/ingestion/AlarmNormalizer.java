package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.common.alarm.AlarmSource;

import java.util.Optional;

/**
 * 告警来源标准化器：将来源特定原始事件标准化为统一 {@link NormalizedAlarm}。
 *
 * <p>标准化 SHALL 为无损投影：{@code rawPayload} 保留原始事件全文。
 * 无法识别或字段缺失时返回空，由上层记录拒绝原因。</p>
 */
public interface AlarmNormalizer {

    /**
     * 返回该标准化器处理的来源服务。
     *
     * @return 来源服务
     */
    AlarmSource source();

    /**
     * 标准化原始事件。
     *
     * @param rawPayload 原始事件全文
     * @return 标准化结果（无法识别时为空）
     */
    Optional<NormalizedAlarm> normalize(String rawPayload);
}
