package com.dpom.agent.common.diagnosisevent;

/**
 * Diagnosis Event 的传输无关投递边界。
 */
@FunctionalInterface
public interface DiagnosisEventDeliveryPort {

    /**
     * 投递一份已规范化且通过完整性校验的事件。
     *
     * @param request 投递请求
     * @return 下游确认
     */
    DeliveryAcknowledgement deliver(DiagnosisEventDeliveryRequest request);
}
