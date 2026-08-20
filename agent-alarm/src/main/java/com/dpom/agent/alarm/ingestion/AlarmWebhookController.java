package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.common.alarm.AlarmSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警 webhook 接入控制器：按来源服务分发到标准化器并接入。
 *
 * <p>签名校验为配置钩子（{@code dpom.alarm.webhook.signature-enabled}，默认关闭）；
 * 默认由部署边界统一处理认证与签名，本控制器不持有密钥。</p>
 */
@RestController
@RequestMapping("/api/v1/alarms/webhook")
public class AlarmWebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(AlarmWebhookController.class);

    private final AlarmIngestionService ingestionService;
    private final boolean signatureEnabled;

    /**
     * 构造 webhook 控制器。
     *
     * @param ingestionService 接入服务
     * @param signatureEnabled 是否启用签名校验（默认关闭，由部署边界处理）
     */
    public AlarmWebhookController(AlarmIngestionService ingestionService,
            @Value("${dpom.alarm.webhook.signature-enabled:false}") boolean signatureEnabled) {
        this.ingestionService = ingestionService;
        this.signatureEnabled = signatureEnabled;
    }

    /**
     * 接收来源告警事件。
     *
     * @param source  来源服务（AOM/CES/APM/LTS）
     * @param payload 原始事件全文
     * @return 接入结果
     */
    @PostMapping("/{source}")
    public ResponseEntity<AlarmIngestionResult> receive(@PathVariable String source,
            @RequestBody String payload) {
        if (signatureEnabled) {
            LOG.debug("签名校验已启用，由部署边界注入校验器");
        }
        AlarmSource parsed;
        try {
            parsed = AlarmSource.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(AlarmIngestionResult.rejected("未知来源: " + source));
        }
        AlarmIngestionResult result = ingestionService.ingest(parsed, payload, "webhook");
        HttpStatus status = result.accepted() ? HttpStatus.ACCEPTED : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(result);
    }
}
