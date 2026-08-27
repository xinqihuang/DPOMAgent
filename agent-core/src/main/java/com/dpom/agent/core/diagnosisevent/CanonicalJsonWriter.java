package com.dpom.agent.core.diagnosisevent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * RFC 8785 规范 JSON 写入边界。
 */
@FunctionalInterface
public interface CanonicalJsonWriter {

    /**
     * 将 JSON 树写为规范 UTF-8 字节。
     *
     * @param value JSON 树
     * @return 规范 UTF-8 字节
     */
    byte[] write(JsonNode value);
}
