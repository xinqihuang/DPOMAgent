package com.dpom.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 非法/未知 mode 必须启动失败，不得静默降级为 development。
 */
class UnknownModeFailsStartupTest {

    @Test
    void unknownModeFailsStartup() {
        Throwable thrown = catchThrowable(() -> SpringApplication.run(DpomAgentApplication.class,
                "--dpom.handoff.mode=bogus", "--spring.main.web-application-type=none"));
        assertThat(thrown).isNotNull();
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertThat(root).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown handoff mode");
    }
}
