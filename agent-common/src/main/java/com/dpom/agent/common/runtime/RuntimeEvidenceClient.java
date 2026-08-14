package com.dpom.agent.common.runtime;

import java.util.List;

/**
 * 运行时证据客户端契约：Core 只依赖本接口，不依赖任何远端 DTO。
 *
 * <p>实现位于 agent-adapter-runtime，背后对接 DPOMBaseMCPServer。不得编造 service/env/trace 标识。</p>
 */
public interface RuntimeEvidenceClient {

    /**
     * 搜索应用日志。
     *
     * @param serviceCode 服务编码
     * @param environment 环境
     * @param keyword     关键字
     * @param timeRange   时间范围
     * @return 证据输入列表（可为空）
     */
    List<ObservationInput> searchLogs(String serviceCode, String environment, String keyword, String timeRange);

    /**
     * 查询 APM 调用链。
     *
     * @param traceId Trace id
     * @return 证据输入列表（可为空）
     */
    List<ObservationInput> queryTrace(String traceId);

    /**
     * 查询告警/事件摘要。
     *
     * @param serviceCode 服务编码
     * @param environment 环境
     * @param timeRange   时间范围
     * @return 证据输入列表（可为空）
     */
    List<ObservationInput> queryAlerts(String serviceCode, String environment, String timeRange);

    /**
     * 查询指标。
     *
     * @param serviceCode 服务编码
     * @param metricName  指标名
     * @param timeRange   时间范围
     * @return 证据输入列表（可为空）
     */
    List<ObservationInput> queryMetrics(String serviceCode, String metricName, String timeRange);
}
