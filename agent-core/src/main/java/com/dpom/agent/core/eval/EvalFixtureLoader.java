package com.dpom.agent.core.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 评测夹具加载器：从案例目录读取 incident.json、expected.json 与 logs.txt。
 */
public class EvalFixtureLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 加载一个案例目录。
     *
     * @param caseDir 案例目录
     * @return 评测案例
     * @throws IOException 读取失败
     */
    public EvalCase load(Path caseDir) throws IOException {
        JsonNode incident = mapper.readTree(caseDir.resolve("incident.json").toFile());
        EvalExpected expected = mapper.readValue(caseDir.resolve("expected.json").toFile(), EvalExpected.class);
        List<String> logs = Files.readAllLines(caseDir.resolve("logs.txt"));
        return new EvalCase(caseDir.getFileName().toString(), incident.path("serviceCode").asText(),
                incident.path("environment").asText(), incident.path("release").asText(),
                incident.path("commit").asText(), incident.path("symptom").asText(), logs, expected);
    }
}
