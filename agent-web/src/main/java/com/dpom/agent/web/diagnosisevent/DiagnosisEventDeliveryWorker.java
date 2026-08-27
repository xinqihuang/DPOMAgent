package com.dpom.agent.web.diagnosisevent;

import com.dpom.agent.core.diagnosisevent.DiagnosisEventDeliveryService;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 仅在外发显式启用时装配的有界轮询工作者。
 */
public final class DiagnosisEventDeliveryWorker {

    private final DiagnosisEventDeliveryService deliveryService;
    private final String workerId;

    /** 创建单实例工作者。 */
    public DiagnosisEventDeliveryWorker(DiagnosisEventDeliveryService deliveryService, String workerId) {
        this.deliveryService = deliveryService;
        this.workerId = workerId;
    }

    /** 按配置间隔投递一个有界批次。 */
    @Scheduled(fixedDelayString = "${dpom.evaluation.delivery.poll-delay:1s}")
    public void deliver() {
        deliveryService.deliverReady(workerId);
    }
}
