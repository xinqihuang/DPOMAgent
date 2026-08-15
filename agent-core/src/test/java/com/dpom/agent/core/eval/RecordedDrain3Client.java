package com.dpom.agent.core.eval;

import com.dpom.agent.common.logtemplate.LogParseResult;
import com.dpom.agent.common.logtemplate.LogTemplate;
import com.dpom.agent.common.logtemplate.LogTemplateMinerClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 录制 Drain3 客户端：只读 recorded-drain3.json，不接触 expected.json。
 */
public class RecordedDrain3Client implements LogTemplateMinerClient {

    private final List<LogParseResult> results;

    /**
     * 构造。
     *
     * @param recordedFile recorded-drain3.json 路径
     * @throws IOException 读取失败
     */
    public RecordedDrain3Client(Path recordedFile) throws IOException {
        results = new ObjectMapper().readValue(recordedFile.toFile(), new TypeReference<List<LogParseResult>>() {
        });
    }

    @Override
    public List<LogParseResult> parseLogs(List<String> lines) {
        return results;
    }

    @Override
    public List<LogTemplate> listTemplates() {
        return List.of();
    }
}
