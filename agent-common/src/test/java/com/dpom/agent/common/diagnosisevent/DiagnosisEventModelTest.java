package com.dpom.agent.common.diagnosisevent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 传输无关事件模型测试。
 */
class DiagnosisEventModelTest {

    @Test
    void provenanceAndPayloadDefensivelyCopyCallerCollections() {
        List<ProvenanceVersion> skills = new ArrayList<>();
        skills.add(ProvenanceVersion.available("diagnosis", "1.0", null));
        Map<String, Object> content = new HashMap<>();
        content.put("resultType", "ROOT_CAUSE_FOUND");

        DiagnosisEventProvenance provenance = new DiagnosisEventProvenance(
                ProvenanceVersion.available("DPOMAgent", "1.0", null),
                ProvenanceVersion.unavailable("NOT_RECORDED"),
                ProvenanceVersion.unavailable("NOT_RECORDED"), skills, skills,
                ProvenanceSource.available("asset-service", "1.0", "abcdef1"),
                ProvenanceVersion.available("diagnostic-evidence-package", "1.0", null));
        DiagnosisInlinePayload payload = new DiagnosisInlinePayload("diagnosis-summary", "1.0", content);

        skills.clear();
        content.clear();

        assertThat(provenance.skills()).hasSize(1);
        assertThat(provenance.toolContracts()).hasSize(1);
        assertThat(payload.content()).containsEntry("resultType", "ROOT_CAUSE_FOUND");
    }

    @Test
    void deliveryPortReturnsTransportNeutralAcknowledgement() {
        DiagnosisEventDeliveryPort port = request ->
                new DeliveryAcknowledgement(DeliveryOutcome.ACCEPTED, null);

        DeliveryAcknowledgement acknowledgement = port.deliver(new DiagnosisEventDeliveryRequest(
                "event-1", "idempotency-1", "{}", "0".repeat(64)));

        assertThat(acknowledgement.outcome()).isEqualTo(DeliveryOutcome.ACCEPTED);
    }
}
