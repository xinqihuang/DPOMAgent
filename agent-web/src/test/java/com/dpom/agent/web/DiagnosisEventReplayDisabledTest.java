package com.dpom.agent.web;

import com.dpom.agent.web.diagnosisevent.DiagnosisEventReplayController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认启动不暴露内部重放端点。
 */
@SpringBootTest
class DiagnosisEventReplayDisabledTest {

    @Autowired private ApplicationContext context;

    @Test
    void replayControllerIsAbsentByDefault() {
        assertThat(context.getBeansOfType(DiagnosisEventReplayController.class)).isEmpty();
    }
}
