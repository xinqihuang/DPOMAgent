package com.dpom.agent.web.diagnosisprogress;

import com.dpom.agent.core.diagnosisprogress.AuthorityProgressDeliveryService;
import org.springframework.scheduling.annotation.Scheduled;

/** 唯一、有界地轮询权威 Diagnosis Progress Outbox。 */
public final class AuthorityProgressDeliveryWorker {

    private final AuthorityProgressDeliveryService service;
    private final String workerId;

    public AuthorityProgressDeliveryWorker(AuthorityProgressDeliveryService service, String workerId) {
        this.service = service;
        this.workerId = workerId;
    }

    /** 按配置周期获取短租约并投递。 */
    @Scheduled(fixedDelayString = "${dpom.evaluation.delivery.poll-delay:1s}")
    public void deliver() {
        service.deliverReady(workerId);
    }
}
