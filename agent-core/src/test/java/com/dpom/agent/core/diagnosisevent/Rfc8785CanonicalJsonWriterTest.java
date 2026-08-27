package com.dpom.agent.core.diagnosisevent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RFC 8785 规范化向量测试。
 */
class Rfc8785CanonicalJsonWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CanonicalJsonWriter writer;

    @BeforeEach
    void setUp() {
        writer = new Rfc8785CanonicalJsonWriter(objectMapper);
    }

    @Test
    void ordersObjectKeys() throws Exception {
        assertCanonical("{\"z\":3,\"a\":1}", "{\"a\":1,\"z\":3}");
    }

    @Test
    void preservesUnicodeWithoutAsciiEscaping() throws Exception {
        assertCanonical("{\"é\":\"café\",\"a\":\"雪\"}", "{\"a\":\"雪\",\"é\":\"café\"}");
    }

    @Test
    void normalizesIntegersAndSupportedNumericValues() throws Exception {
        assertCanonical("{\"whole\":1.0,\"fraction\":0.000001,\"large\":1e+30}",
                "{\"fraction\":0.000001,\"large\":1e+30,\"whole\":1}");
    }

    private void assertCanonical(String input, String expected) throws Exception {
        JsonNode tree = objectMapper.readTree(input);
        assertThat(new String(writer.write(tree), StandardCharsets.UTF_8)).isEqualTo(expected);
    }
}
