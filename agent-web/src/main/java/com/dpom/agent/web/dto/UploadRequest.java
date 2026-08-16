package com.dpom.agent.web.dto;

/**
 * 上传请求：只携带 packageId，不携带 approval 布尔；批准只能来自数据库既有 APPROVED 状态。
 *
 * @param packageId 包标识
 */
public record UploadRequest(String packageId) {
}
