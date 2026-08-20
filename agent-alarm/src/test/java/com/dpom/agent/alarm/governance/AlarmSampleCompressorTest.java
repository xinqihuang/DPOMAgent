package com.dpom.agent.alarm.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 告警压缩采样器单测：覆盖首末保留、上限抽样与空输入。
 */
class AlarmSampleCompressorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void underLimitKeepsAll() throws Exception {
        AlarmSampleCompressor compressor = new AlarmSampleCompressor(5);
        String json = compressor.compress(null, "s1");
        json = compressor.compress(json, "s2");
        json = compressor.compress(json, "s3");
        List<String> samples = MAPPER.readerForListOf(String.class).readValue(json);
        assertThat(samples).containsExactly("s1", "s2", "s3");
    }

    @Test
    void overLimitKeepsFirstLastAndCaps() throws Exception {
        AlarmSampleCompressor compressor = new AlarmSampleCompressor(5);
        String json = null;
        for (int i = 1; i <= 10; i++) {
            json = compressor.compress(json, "s" + i);
        }
        List<String> samples = MAPPER.readerForListOf(String.class).readValue(json);
        assertThat(samples).hasSize(5);
        assertThat(samples.get(0)).isEqualTo("s1");
        assertThat(samples.get(4)).isEqualTo("s10");
    }

    @Test
    void nullNewSampleSkipped() throws Exception {
        AlarmSampleCompressor compressor = new AlarmSampleCompressor(5);
        String json = compressor.compress("[\"s1\"]", null);
        List<String> samples = MAPPER.readerForListOf(String.class).readValue(json);
        assertThat(samples).containsExactly("s1");
    }

    @Test
    void malformedCurrentTreatedAsEmpty() throws Exception {
        AlarmSampleCompressor compressor = new AlarmSampleCompressor(5);
        String json = compressor.compress("not-json", "s1");
        List<String> samples = MAPPER.readerForListOf(String.class).readValue(json);
        assertThat(samples).containsExactly("s1");
    }
}
