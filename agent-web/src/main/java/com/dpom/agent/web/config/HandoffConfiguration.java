package com.dpom.agent.web.config;

import com.dpom.agent.common.handoff.EvidenceHandoffStore;
import com.dpom.agent.core.handoff.DiagnosticEvidencePackageBuilder;
import com.dpom.agent.core.handoff.DiagnosticEvidencePackageParser;
import com.dpom.agent.core.handoff.EscalationEvaluator;
import com.dpom.agent.core.handoff.EvidenceHandoffService;
import com.dpom.agent.core.handoff.HandoffConfig;
import com.dpom.agent.core.handoff.HandoffProfile;
import com.dpom.agent.core.handoff.PackageSerializer;
import com.dpom.agent.core.handoff.PackageVerifier;
import com.dpom.agent.core.persistence.ConclusionDao;
import com.dpom.agent.core.persistence.EvidenceBundleDao;
import com.dpom.agent.core.persistence.EvidenceHandoffDao;
import com.dpom.agent.core.persistence.HypothesisDao;
import com.dpom.agent.core.persistence.IncidentDao;
import com.dpom.agent.core.persistence.InvestigationDao;
import com.dpom.agent.web.handoff.DisabledEvidenceHandoffStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 证据交接装配：production/development 共享同一 core 引擎。store 默认禁用（正式 Profile 禁止内存假存储）；
 * mode 非法即启动失败；接口按 Profile 由 Controller 条件装配控制。
 */
@Configuration
public class HandoffConfiguration {

    @Bean
    public HandoffConfig handoffConfig(
            @Value("${dpom.handoff.mode:development}") String mode,
            @Value("${dpom.handoff.confidence-threshold:60}") int confidenceThreshold,
            @Value("${dpom.handoff.max-package-bytes:1048576}") int maxPackageBytes,
            @Value("${dpom.handoff.max-package-entries:200}") int maxPackageEntries,
            @Value("${dpom.handoff.schema-version:1}") int schemaVersion,
            @Value("${dpom.handoff.obs.enabled:false}") boolean obsEnabled,
            @Value("${dpom.handoff.obs.bucket:}") String bucket,
            @Value("${dpom.handoff.obs.prefix:}") String prefix,
            @Value("${dpom.handoff.approval-ttl-seconds:3600}") int approvalTtlSeconds) {
        HandoffProfile profile = HandoffProfile.from(mode);
        return new HandoffConfig(profile, confidenceThreshold, maxPackageBytes, maxPackageEntries, schemaVersion,
                obsEnabled, bucket, prefix, approvalTtlSeconds);
    }

    @Bean
    public EvidenceHandoffStore evidenceHandoffStore() {
        return new DisabledEvidenceHandoffStore();
    }

    @Bean
    public EscalationEvaluator escalationEvaluator() {
        return new EscalationEvaluator();
    }

    @Bean
    public DiagnosticEvidencePackageBuilder diagnosticEvidencePackageBuilder(HandoffConfig config) {
        return new DiagnosticEvidencePackageBuilder(config);
    }

    @Bean
    public PackageSerializer packageSerializer() {
        return new PackageSerializer();
    }

    @Bean
    public PackageVerifier packageVerifier() {
        return new PackageVerifier();
    }

    @Bean
    public DiagnosticEvidencePackageParser diagnosticEvidencePackageParser() {
        return new DiagnosticEvidencePackageParser();
    }

    @Bean
    public EvidenceHandoffService evidenceHandoffService(InvestigationDao investigationDao, IncidentDao incidentDao,
            ConclusionDao conclusionDao, HypothesisDao hypothesisDao, EvidenceBundleDao evidenceBundleDao,
            EvidenceHandoffDao handoffDao, EscalationEvaluator evaluator, DiagnosticEvidencePackageBuilder builder,
            PackageSerializer serializer, PackageVerifier verifier, DiagnosticEvidencePackageParser parser,
            EvidenceHandoffStore store, HandoffConfig config) {
        return new EvidenceHandoffService(investigationDao, incidentDao, conclusionDao, hypothesisDao,
                evidenceBundleDao, handoffDao, evaluator, builder, serializer, verifier, parser, store, config);
    }
}
