package com.dpom.agent.core.persistence.command;

import java.time.LocalDateTime;

/**
 * DEAD 事件重置命令。
 */
public record DiagnosisEventReplayCommand(long id, LocalDateTime now) {
}
