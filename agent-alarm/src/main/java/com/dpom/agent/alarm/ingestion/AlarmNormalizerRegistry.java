package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.common.alarm.AlarmSource;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 告警标准化器注册表：按来源服务查找对应标准化器。
 */
@Component
public class AlarmNormalizerRegistry {

    private final Map<AlarmSource, AlarmNormalizer> normalizers;

    /**
     * 构造注册表。
     *
     * @param all 所有标准化器（由 Spring 注入）
     */
    public AlarmNormalizerRegistry(List<AlarmNormalizer> all) {
        this.normalizers = new EnumMap<>(AlarmSource.class);
        for (AlarmNormalizer normalizer : all) {
            this.normalizers.put(normalizer.source(), normalizer);
        }
    }

    /**
     * 按来源查找标准化器。
     *
     * @param source 来源服务
     * @return 标准化器（未知来源时为空）
     */
    public Optional<AlarmNormalizer> get(AlarmSource source) {
        return Optional.ofNullable(normalizers.get(source));
    }
}
