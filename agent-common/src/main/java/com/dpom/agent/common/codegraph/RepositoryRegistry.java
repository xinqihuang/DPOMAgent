package com.dpom.agent.common.codegraph;

/**
 * 代码仓库注册表：把 serviceCode + commit 确定映射到快照根目录。
 *
 * <p>不允许「找不到就选第一个仓库」；未知服务与 commit 不一致均 fail closed。</p>
 */
public interface RepositoryRegistry {

    /**
     * 解析已注册仓库。
     *
     * @param serviceCode 服务编码
     * @param commitSha   提交 SHA
     * @return 已注册仓库
     * @throws SnapshotNotFoundException 服务未注册
     * @throws CommitMismatchException   提交不匹配
     */
    RegisteredRepository resolve(String serviceCode, String commitSha);

    /**
     * 按已解析的 projectPath（快照根）反查并验证仓库。
     *
     * <p>用于查询前校验 projectPath 确实是注册表解析出的受控快照根，禁止任意路径直接进入 MCP 参数。</p>
     *
     * @param projectPath 快照根路径
     * @return 已注册仓库
     * @throws SnapshotNotFoundException projectPath 未注册或越界
     */
    RegisteredRepository resolveByProjectPath(String projectPath);
}
