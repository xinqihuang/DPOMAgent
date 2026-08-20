package com.dpom.agent.alarm.ingestion;

import com.dpom.agent.alarm.governance.AlarmDeduplicator;
import com.dpom.agent.alarm.governance.AlarmFingerprint;
import com.dpom.agent.alarm.governance.AlarmSampleCompressor;
import com.dpom.agent.alarm.governance.DedupDecision;
import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.AlarmDao;
import com.dpom.agent.alarm.persistence.command.AlarmAuditInsert;
import com.dpom.agent.alarm.persistence.command.AlarmInsert;
import com.dpom.agent.common.alarm.AlarmSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 告警接入服务：标准化、指纹计算、去重合并/落库与审计。
 *
 * <p>去重合并由 {@link AlarmDeduplicator} 判定；接入与拒绝均写审计。</p>
 */
@Service
public class AlarmIngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(AlarmIngestionService.class);

    private final AlarmNormalizerRegistry registry;
    private final AlarmDao alarmDao;
    private final AlarmAuditDao auditDao;
    private final AlarmDeduplicator deduplicator;
    private final AlarmSampleCompressor sampleCompressor;

    /**
     * 构造接入服务。
     *
     * @param registry        标准化器注册表
     * @param alarmDao        告警持久化
     * @param auditDao        审计持久化
     * @param deduplicator    去重器
     * @param sampleCompressor 压缩采样器
     */
    public AlarmIngestionService(AlarmNormalizerRegistry registry, AlarmDao alarmDao, AlarmAuditDao auditDao,
            AlarmDeduplicator deduplicator, AlarmSampleCompressor sampleCompressor) {
        this.registry = registry;
        this.alarmDao = alarmDao;
        this.auditDao = auditDao;
        this.deduplicator = deduplicator;
        this.sampleCompressor = sampleCompressor;
    }

    /**
     * 接入一条原始告警。
     *
     * @param source        来源服务
     * @param rawPayload    原始事件全文
     * @param ingestionMode 接入方式（webhook/poll）
     * @return 接入结果
     */
    public AlarmIngestionResult ingest(AlarmSource source, String rawPayload, String ingestionMode) {
        Optional<AlarmNormalizer> normalizer = registry.get(source);
        if (normalizer.isEmpty()) {
            return reject(source, ingestionMode, "未知来源: " + source);
        }
        Optional<NormalizedAlarm> normalized = normalizer.get().normalize(rawPayload);
        if (normalized.isEmpty()) {
            return reject(source, ingestionMode, "标准化失败或必填字段缺失");
        }
        return persist(normalized.get(), ingestionMode);
    }

    private AlarmIngestionResult persist(NormalizedAlarm alarm, String ingestionMode) {
        String fingerprint = AlarmFingerprint.of(alarm.source(), alarm.resourceId(), alarm.alarmName(),
                alarm.severity());
        DedupDecision decision = deduplicator.evaluate(alarm, fingerprint);
        long alarmId;
        String result;
        if (decision.merge()) {
            String samples = compressSamples(decision.existingAlarmId(), alarm.rawPayload());
            alarmDao.mergeOccurrence(decision.existingAlarmId(), alarm.occurredAt(), samples);
            alarmId = decision.existingAlarmId();
            result = "MERGED";
        } else {
            alarmId = insertNew(alarm, ingestionMode, fingerprint);
            result = "ACCEPTED";
        }
        auditDao.insert(new AlarmAuditInsert("INGEST", "ALARM", alarmId, ingestionMode,
                alarm.source().name(), result));
        LOG.info("告警接入 alarmId={} source={} result={}", alarmId, alarm.source(), result);
        return AlarmIngestionResult.accepted(alarmId);
    }

    private long insertNew(NormalizedAlarm alarm, String ingestionMode, String fingerprint) {
        AlarmInsert command = new AlarmInsert(alarm.source(), ingestionMode, alarm.externalId(), fingerprint,
                alarm.resourceId(), alarm.alarmName(), alarm.severity(), alarm.status(), 1, alarm.occurredAt(),
                alarm.occurredAt(), alarm.serviceCode(), alarm.environment(), alarm.rawPayload(), null);
        alarmDao.insert(command);
        return command.getId();
    }

    private String compressSamples(long existingId, String newSample) {
        String existing = alarmDao.findById(existingId).map(Alarm::samplePayloads).orElse(null);
        return sampleCompressor.compress(existing, newSample);
    }

    private AlarmIngestionResult reject(AlarmSource source, String ingestionMode, String reason) {
        auditDao.insert(new AlarmAuditInsert("INGEST_REJECT", "ALARM", null, ingestionMode,
                source == null ? "UNKNOWN" : source.name(), reason));
        LOG.warn("告警拒绝 source={} reason={}", source, reason);
        return AlarmIngestionResult.rejected(reason);
    }
}
