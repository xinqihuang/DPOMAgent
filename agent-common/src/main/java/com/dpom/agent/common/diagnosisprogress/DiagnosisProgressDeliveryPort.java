package com.dpom.agent.common.diagnosisprogress;

import com.dpom.agent.common.diagnosisevent.DeliveryAcknowledgement;

/** 发送一份已经冻结的权威 Diagnosis Progress 记录。 */
@FunctionalInterface
public interface DiagnosisProgressDeliveryPort {

    /** 发送记录并返回有界、稳定的确认结果。 */
    DeliveryAcknowledgement deliver(DiagnosisProgressDeliveryRequest request);
}
