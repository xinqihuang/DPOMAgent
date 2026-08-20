package com.dpom.agent.alarm.governance;

import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.ingestion.NormalizedAlarm;
import com.dpom.agent.alarm.persistence.AlarmDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 告警去重器：按指纹与时间窗判定合并或新建，MySQL 为权威，指纹缓存为快速路径。
 */
@Service
public class AlarmDeduplicator {

    private final AlarmDao alarmDao;
    private final AlarmFingerprintCache cache;
    private final Duration window;

    /**
     * 构造去重器。
     *
     * @param alarmDao     告警持久化
     * @param cache        指纹缓存
     * @param windowMinutes 去重窗（分钟）
     */
    public AlarmDeduplicator(AlarmDao alarmDao, AlarmFingerprintCache cache,
            @Value("${dpom.alarm.dedup.window-minutes:5}") long windowMinutes) {
        this.alarmDao = alarmDao;
        this.cache = cache;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /**
     * 评估去重决策。
     *
     * @param alarm       标准化告警
     * @param fingerprint 指纹
     * @return 去重决策
     */
    public DedupDecision evaluate(NormalizedAlarm alarm, String fingerprint) {
        Optional<Long> cached = cache.existingId(fingerprint);
        if (cached.isPresent()) {
            Optional<Alarm> existing = alarmDao.findById(cached.get());
            if (existing.isPresent() && withinWindow(existing.get(), alarm)) {
                return DedupDecision.merge(existing.get().id());
            }
        }
        Optional<Alarm> latest = alarmDao.findLatestByFingerprint(fingerprint);
        if (latest.isPresent() && withinWindow(latest.get(), alarm)) {
            cache.remember(fingerprint, latest.get().id());
            return DedupDecision.merge(latest.get().id());
        }
        return DedupDecision.newAlarm();
    }

    private boolean withinWindow(Alarm existing, NormalizedAlarm alarm) {
        if (existing.lastOccurredAt() == null || alarm.occurredAt() == null) {
            return false;
        }
        return !alarm.occurredAt().isAfter(existing.lastOccurredAt().plus(window));
    }
}
