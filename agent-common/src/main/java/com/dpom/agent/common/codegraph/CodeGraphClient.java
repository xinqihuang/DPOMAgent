package com.dpom.agent.common.codegraph;

import java.util.List;

/**
 * 代码图客户端契约：Core 只依赖本接口，不依赖任何远端 DTO。
 *
 * <p>实现位于 agent-adapter-codegraph，背后对接 CodeGraph（colbymchenry/codegraph）官方 stdio MCP。</p>
 */
public interface CodeGraphClient {

    /**
     * 解析快照。
     *
     * @param serviceCode 服务编码
     * @param commitSha   提交 SHA
     * @return 快照
     * @throws SnapshotNotFoundException 快照不存在
     * @throws SnapshotNotReadyException 快照未就绪
     */
    CodeSnapshot resolveSnapshot(String serviceCode, String commitSha);

    /**
     * 获取快照。
     *
     * @param snapshotId 快照 id
     * @return 快照
     * @throws SnapshotNotFoundException 快照不存在
     * @throws SnapshotNotReadyException 快照未就绪
     */
    CodeSnapshot getSnapshot(String snapshotId);

    /**
     * 查找符号。
     *
     * @param snapshotId 快照 id
     * @param name       符号名
     * @return 符号列表
     */
    List<Symbol> findSymbol(String snapshotId, String name);

    /**
     * 查找调用方。
     *
     * @param snapshotId 快照 id
     * @param symbol     符号名
     * @return 调用方列表
     */
    List<Symbol> findCallers(String snapshotId, String symbol);

    /**
     * 查找被调用方。
     *
     * @param snapshotId 快照 id
     * @param symbol     符号名
     * @return 被调用方列表
     */
    List<Symbol> findCallees(String snapshotId, String symbol);

    /**
     * 查找调用链。
     *
     * @param snapshotId 快照 id
     * @param fromSymbol 起始符号
     * @param toSymbol   目标符号
     * @return 调用链步骤列表
     */
    List<CallStep> findCallChain(String snapshotId, String fromSymbol, String toSymbol);

    /**
     * 查找类继承层次。
     *
     * @param snapshotId 快照 id
     * @param className  类名
     * @return 继承层次
     */
    ClassHierarchy findClassHierarchy(String snapshotId, String className);

    /**
     * 查找受变更影响的符号（有界图摘要）。
     *
     * <p>兼容默认实现：不支持影响面分析的实现返回空列表，不破坏现有调用方。</p>
     *
     * @param snapshotId 快照 id
     * @param symbol     符号名
     * @return 受影响的符号列表（有界）
     */
    default List<Symbol> findImpact(String snapshotId, String symbol) {
        return List.of();
    }
}
