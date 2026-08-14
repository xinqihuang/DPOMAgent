package com.dpom.agent.common.logtemplate;

import java.util.List;

/**
 * 日志模板挖掘客户端契约：把应用日志聚类为模板并抽取参数（背后为 Drain3）。
 */
public interface LogTemplateMinerClient {

    /**
     * 批量解析日志。
     *
     * @param lines 日志行
     * @return 解析结果列表
     */
    List<LogParseResult> parseLogs(List<String> lines);

    /**
     * 列出已学习模板。
     *
     * @return 模板列表
     */
    List<LogTemplate> listTemplates();
}
