package com.dpom.agent.web.dto;

import java.util.List;

/**
 * 研发侧导入响应（纯 DTO，不含领域类型）。
 *
 * @param packageId      包标识
 * @param alreadyImported 是否已导入（幂等命中）
 * @param service        服务编码
 * @param release        发布版本
 * @param commit         提交 SHA
 * @param degradations   恢复的降级标记
 * @param contradictions 恢复的矛盾标记
 */
public record HandoffImportResponse(String packageId, boolean alreadyImported, String service, String release,
                                    String commit, List<String> degradations, List<String> contradictions) {
}
