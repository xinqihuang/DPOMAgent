package com.dpom.agent.common.alarm;

/**
 * 处置工件生成端口：由 agent-core 实现，agent-alarm 仅依赖本抽象。
 *
 * <p>端口仅生成工件（带 {@code REQUIRES_APPROVAL}），不执行任何生产操作；
 * 端口未装配时 agent-alarm 安全降级（记录跳过、不抛异常）。</p>
 */
public interface HandlingArtifactPort {

    /**
     * 生成处置工件。
     *
     * @param request 生成请求
     * @return 生成结果
     */
    HandlingArtifactResult generate(HandlingArtifactRequest request);
}
