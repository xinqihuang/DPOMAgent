package com.dpom.agent.common.codegraph;

/**
 * 代码快照：绑定服务编码与提交，携带工作区路径。
 *
 * @param snapshotId    快照 id
 * @param serviceCode   服务编码
 * @param commitSha     提交 SHA
 * @param workspacePath 工作区路径
 * @param status        快照状态
 */
public record CodeSnapshot(String snapshotId, String serviceCode, String commitSha,
                           String workspacePath, SnapshotStatus status) {
}
