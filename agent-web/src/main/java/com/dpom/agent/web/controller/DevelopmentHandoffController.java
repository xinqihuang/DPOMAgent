package com.dpom.agent.web.controller;

import com.dpom.agent.core.handoff.EvidenceHandoffService;
import com.dpom.agent.core.handoff.ImportResult;
import com.dpom.agent.web.dto.HandoffImportResponse;
import com.dpom.agent.web.dto.HandoffVerifyRequest;
import com.dpom.agent.web.metrics.HandoffMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 研发区域证据交接控制器：下载、校验、导入/恢复。仅 development Profile 装配（缺省为 development）。
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "dpom.handoff.mode", havingValue = "development", matchIfMissing = true)
public class DevelopmentHandoffController {

    private final EvidenceHandoffService service;
    private final HandoffMetrics metrics;

    /**
     * 构造器注入。
     *
     * @param service 交接服务
     * @param metrics 交接指标（best-effort）
     */
    public DevelopmentHandoffController(EvidenceHandoffService service, HandoffMetrics metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    /**
     * 研发侧下载、校验并导入（幂等）。
     */
    @PostMapping("/handoff/verify")
    public HandoffImportResponse verify(@RequestBody HandoffVerifyRequest request) {
        ImportResult result = service.verifyAndImport(request.objectKey(), request.expectedService(),
                request.expectedRelease(), request.expectedCommit());
        recordImport();
        return new HandoffImportResponse(result.packageId(), result.alreadyImported(), result.bundle().service(),
                result.bundle().release(), result.bundle().commit(), result.bundle().degradations(),
                result.bundle().contradictions());
    }

    private void recordImport() {
        try {
            metrics.recordImport();
        } catch (RuntimeException ignored) {
            // 指标失败不得改变业务结果
        }
    }
}
