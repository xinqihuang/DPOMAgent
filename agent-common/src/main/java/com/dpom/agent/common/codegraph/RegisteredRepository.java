package com.dpom.agent.common.codegraph;

import java.nio.file.Path;

/**
 * 已注册代码仓库：serviceCode + release/commit 到快照根目录的确定映射。
 *
 * @param serviceCode  服务编码
 * @param release      发布版本
 * @param commitSha    提交 SHA
 * @param snapshotRoot 快照根目录（CodeGraph projectPath 的来源）
 */
public record RegisteredRepository(String serviceCode, String release, String commitSha, Path snapshotRoot) {
}
