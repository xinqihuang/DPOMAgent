package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.domain.NotificationChannel;
import com.dpom.agent.alarm.domain.NotificationRule;
import com.dpom.agent.alarm.domain.NotificationStatus;
import com.dpom.agent.alarm.persistence.NotificationRecordDao;
import com.dpom.agent.alarm.persistence.command.NotificationRecordInsert;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 通知分派服务：解析规则渠道配置 → 按渠道发送 → 记录每条发送结果与时间。
 *
 * <p>无匹配规则时不发送。所有发送结果（含失败）写 {@code notification_record}。</p>
 */
@Service
public class NotificationDispatchService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final Map<NotificationChannel, NotificationSender> senders;
    private final NotificationRecordDao recordDao;
    private final ObjectMapper objectMapper;

    /**
     * 构造分派服务。
     *
     * @param senderList  各渠道发送器（Spring 自动注入）
     * @param recordDao   通知记录持久化
     * @param objectMapper JSON 解析
     */
    public NotificationDispatchService(List<NotificationSender> senderList, NotificationRecordDao recordDao,
            ObjectMapper objectMapper) {
        this.senders = new EnumMap<>(NotificationChannel.class);
        for (NotificationSender sender : senderList) {
            this.senders.put(sender.channel(), sender);
        }
        this.recordDao = recordDao;
        this.objectMapper = objectMapper;
    }

    /**
     * 对命中规则分派通知并记录结果。
     *
     * @param incidentId 事件 id
     * @param rules      命中规则列表
     * @param subject    通知主题
     * @param body       通知正文
     */
    public void dispatch(long incidentId, List<NotificationRule> rules, String subject, String body) {
        for (NotificationRule rule : rules) {
            dispatchRule(incidentId, rule, subject, body);
        }
    }

    private void dispatchRule(long incidentId, NotificationRule rule, String subject, String body) {
        List<ChannelTarget> targets = parseChannels(rule.channels());
        for (ChannelTarget target : targets) {
            sendAndRecord(incidentId, rule, target, subject, body);
        }
    }

    private void sendAndRecord(long incidentId, NotificationRule rule, ChannelTarget target, String subject,
            String body) {
        NotificationSender sender = senders.get(target.channel());
        NotificationStatus status;
        String errorMessage = null;
        if (sender == null) {
            status = NotificationStatus.SKIPPED;
            errorMessage = "无 " + target.channel() + " 渠道发送器";
        } else {
            NotificationMessage msg = new NotificationMessage(incidentId, subject, body, target);
            SendOutcome outcome = sender.send(msg);
            status = outcome.success() ? NotificationStatus.SENT : NotificationStatus.FAILED;
            errorMessage = outcome.errorMessage();
        }
        LocalDateTime now = LocalDateTime.now();
        recordDao.insert(new NotificationRecordInsert(incidentId, rule.id(), target.channel(),
                target.recipient(), status, errorMessage, now));
        LOG.info("通知记录 incidentId={} ruleId={} channel={} status={}", incidentId, rule.id(),
                target.channel(), status);
    }

    private List<ChannelTarget> parseChannels(String channelsJson) {
        if (channelsJson == null || channelsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(channelsJson, new TypeReference<List<ChannelTarget>>() {
            });
        } catch (Exception e) {
            LOG.warn("解析渠道配置失败: {}", e.getMessage());
            return List.of();
        }
    }
}
