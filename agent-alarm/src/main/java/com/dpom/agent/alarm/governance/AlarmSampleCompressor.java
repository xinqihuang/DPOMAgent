package com.dpom.agent.alarm.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警压缩采样：保留首末与代表性中间样本，不保留无界全量重复。
 *
 * <p>样本以 JSON 数组形式存入 {@code sample_payloads}；超过上限时按首末 + 等距中间抽样。</p>
 */
@Service
public class AlarmSampleCompressor {

    private static final Logger LOG = LoggerFactory.getLogger(AlarmSampleCompressor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int maxSamples;

    /**
     * 构造压缩器。
     *
     * @param maxSamples 最大样本数
     */
    public AlarmSampleCompressor(@Value("${dpom.alarm.sample.max:5}") int maxSamples) {
        this.maxSamples = maxSamples;
    }

    /**
     * 追加新样本并压缩。
     *
     * @param currentJson 当前样本 JSON 数组（可为空）
     * @param newSample   新样本（可为空，空时跳过）
     * @return 压缩后样本 JSON 数组
     */
    public String compress(String currentJson, String newSample) {
        List<String> samples = parse(currentJson);
        if (newSample != null) {
            samples.add(newSample);
        }
        List<String> selected = selectSamples(samples);
        return serialize(selected);
    }

    private List<String> selectSamples(List<String> samples) {
        if (samples.size() <= maxSamples) {
            return samples;
        }
        List<String> result = new ArrayList<>();
        result.add(samples.get(0));
        int middleCount = maxSamples - 2;
        for (int i = 1; i <= middleCount; i++) {
            int index = (int) ((long) i * (samples.size() - 1) / (middleCount + 1));
            result.add(samples.get(index));
        }
        result.add(samples.get(samples.size() - 1));
        return result;
    }

    private static List<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(MAPPER.readerForListOf(String.class).readValue(json));
        } catch (JsonProcessingException e) {
            LOG.warn("样本 JSON 解析失败，按空列表处理");
            return new ArrayList<>();
        }
    }

    private static String serialize(List<String> samples) {
        try {
            return MAPPER.writeValueAsString(samples);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
