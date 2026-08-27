package com.dpom.agent.web.diagnosisevent;

import com.dpom.agent.core.diagnosisevent.AuthorityPublicationDeliveryService;
import org.springframework.scheduling.annotation.Scheduled;

/** 投递 DPOMAgent 权威终态 Outbox 的唯一有界工作者。 */
public final class AuthorityPublicationDeliveryWorker {

    private final AuthorityPublicationDeliveryService service;
    private final String workerId;

    /** 创建带稳定进程内身份的工作者。 */
    public AuthorityPublicationDeliveryWorker(AuthorityPublicationDeliveryService service, String workerId) {
        this.service = service;
        this.workerId = workerId;
    }

    /** 按配置周期获取短租约并投递。 */
    @Scheduled(fixedDelayString = "${dpom.evaluation.delivery.poll-delay:1s}")
    public void deliver() {
        service.deliverReady(workerId);
    }
}
