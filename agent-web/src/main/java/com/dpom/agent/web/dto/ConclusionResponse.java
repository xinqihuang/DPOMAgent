package com.dpom.agent.web.dto;

/**
 * 结论响应。
 *
 * @param available  是否已生成
 * @param resultType 结论类型
 * @param rootCauseId 根因标识
 * @param rootCause  根因描述
 * @param evidenceIds 证据引用
 * @param summary    摘要
 */
public record ConclusionResponse(boolean available, String resultType, String rootCauseId, String rootCause,
                                String evidenceIds, String summary) {
}
