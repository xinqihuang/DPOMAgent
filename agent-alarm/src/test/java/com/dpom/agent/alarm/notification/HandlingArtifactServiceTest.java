package com.dpom.agent.alarm.notification;

import com.dpom.agent.alarm.persistence.AlarmAuditDao;
import com.dpom.agent.alarm.persistence.command.AlarmAuditInsert;
import com.dpom.agent.common.alarm.HandlingArtifactPort;
import com.dpom.agent.common.alarm.HandlingArtifactRequest;
import com.dpom.agent.common.alarm.HandlingArtifactResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 处置工件服务单测：断言不执行生产操作、不持凭据、端口未装配安全降级。
 */
@ExtendWith(MockitoExtension.class)
class HandlingArtifactServiceTest {

    @Mock
    private AlarmAuditDao auditDao;

    @Test
    void generatesArtifactWithRequiresApprovalViaPort() {
        HandlingArtifactPort port = req -> new HandlingArtifactResult(42L, "REQUIRES_APPROVAL");
        HandlingArtifactService svc = new HandlingArtifactService(Optional.of(port), auditDao);

        svc.generateArtifact(7L, 100L, "shell", "重启服务", "HIGH", "#!/bin/bash\n");

        ArgumentCaptor<AlarmAuditInsert> captor = ArgumentCaptor.forClass(AlarmAuditInsert.class);
        verify(auditDao).insert(captor.capture());
        assertThat(captor.getValue().getDetail()).contains("REQUIRES_APPROVAL");
    }

    @Test
    void safeDegradesWhenPortAbsent() {
        HandlingArtifactService svc = new HandlingArtifactService(Optional.empty(), auditDao);

        svc.generateArtifact(7L, 100L, "shell", "重启服务", "HIGH", "#!/bin/bash\n");

        ArgumentCaptor<AlarmAuditInsert> captor = ArgumentCaptor.forClass(AlarmAuditInsert.class);
        verify(auditDao).insert(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("ARTIFACT_SKIP");
    }

    @Test
    void requestCarriesNoCredentials() {
        java.util.Set<String> forbidden = java.util.Set.of("ak", "sk", "accesskey", "secretkey", "token",
                "credential", "secret", "password", "cookie");
        java.lang.reflect.RecordComponent[] components = HandlingArtifactRequest.class.getRecordComponents();
        for (java.lang.reflect.RecordComponent c : components) {
            assertThat(forbidden).doesNotContain(c.getName().toLowerCase());
        }
    }

    @Test
    void serviceExposesNoExecutionMethod() {
        java.lang.reflect.Method[] methods = HandlingArtifactService.class.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            String name = m.getName().toLowerCase();
            assertThat(name).doesNotContain("execute").doesNotContain("run").doesNotContain("apply");
        }
    }
}
