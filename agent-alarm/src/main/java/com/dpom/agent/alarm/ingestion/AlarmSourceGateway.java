package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.common.alarm.AlarmSource;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警来源只读网关端口：从 DPOMBaseMCPServer 增量拉取华为云告警。
 *
 * <p>实现位于 agent-web（经 Spring RestClient 调用 DPOMBaseMCPServer 只读查询），
 * 不在 agent-alarm 内持有华为云凭据或 SDK。</p>
 */
public interface AlarmSourceGateway {

    /**
     * 拉取指定来源在游标之后的增量告警事件。
     *
     * @param source 来源服务
     * @param since  游标（不含），可为空表示拉取最近
     * @param limit  单次拉取上限
     * @return 原始事件列表（按发生时间升序）
     */
    List<RawAlarmEvent> fetchSince(AlarmSource source, LocalDateTime since, int limit);
}
