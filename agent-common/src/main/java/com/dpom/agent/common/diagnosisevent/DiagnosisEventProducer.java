package com.dpom.agent.common.diagnosisevent;

/**
 * Diagnosis Event 生产者。
 *
 * @param service    服务名
 * @param instanceId 实例标识
 */
public record DiagnosisEventProducer(String service, String instanceId) {
}
