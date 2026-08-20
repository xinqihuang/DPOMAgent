package com.dpom.agent.common.alarm;

/**
 * 告警事件触发诊断端口：由 agent-core 实现，agent-alarm 仅依赖本抽象。
 *
 * <p>端口未装配时，agent-alarm SHALL 安全降级（记录跳过、不抛异常），不阻塞告警中台自身职责。</p>
 */
public interface AlarmIncidentTriggerPort {

    /**
     * 请求对告警事件启动诊断调查。
     *
     * @param request 触发请求
     * @return 触发结果
     */
    AlarmIncidentTriggerResult trigger(AlarmIncidentTriggerRequest request);
}
