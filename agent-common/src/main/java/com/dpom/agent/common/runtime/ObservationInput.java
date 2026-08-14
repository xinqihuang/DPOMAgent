package com.dpom.agent.common.runtime;

/**
 * 运行时证据输入：用于在 Core 侧形成 Observation 的统一结构。
 *
 * @param artifactRef 工件引用
 * @param summary     证据摘要
 * @param payloadJson 证据负载（JSON，可为空，不保存巨量完整日志）
 */
public record ObservationInput(ArtifactRef artifactRef, String summary, String payloadJson) {
}
