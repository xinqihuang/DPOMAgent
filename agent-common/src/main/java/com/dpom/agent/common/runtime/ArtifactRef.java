package com.dpom.agent.common.runtime;

/**
 * 运行时证据工件引用：指向远端存储的证据，而非完整内容。
 *
 * @param source     来源（logs/trace/metrics/alerts）
 * @param artifactId 工件 id
 * @param location   位置（日志流/时间窗等，可为空）
 */
public record ArtifactRef(String source, String artifactId, String location) {
}
