package com.dpom.agent.web.dto;

/**
 * 研发侧校验/导入请求。
 *
 * @param objectKey       对象名
 * @param expectedService 期望服务编码
 * @param expectedRelease 期望发布版本
 * @param expectedCommit  期望提交 SHA
 */
public record HandoffVerifyRequest(String objectKey, String expectedService, String expectedRelease,
                                   String expectedCommit) {
}
