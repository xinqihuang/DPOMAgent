package com.dpom.agent.core.diagnosisevent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.erdtman.jcs.JsonCanonicalizer;

import java.io.IOException;

/**
 * 使用隔离的 JCS 实现写出 RFC 8785 规范 JSON。
 */
public final class Rfc8785CanonicalJsonWriter implements CanonicalJsonWriter {

    private final ObjectMapper objectMapper;

    /**
     * 创建写入器。
     *
     * @param objectMapper Jackson 映射器
     */
    public Rfc8785CanonicalJsonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] write(JsonNode value) {
        try {
            byte[] source = objectMapper.writeValueAsBytes(value);
            return new JsonCanonicalizer(source).getEncodedUTF8();
        } catch (IOException | IllegalArgumentException e) {
            throw new CanonicalJsonException(e);
        }
    }
}
