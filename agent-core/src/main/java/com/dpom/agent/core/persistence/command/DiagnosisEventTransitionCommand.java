package com.dpom.agent.core.persistence.command;

import java.time.LocalDateTime;

/**
 * 受 fencing token 保护的发件箱状态更新命令。
 */
public record DiagnosisEventTransitionCommand(long id, String leaseToken, LocalDateTime now,
                                              LocalDateTime nextAttemptAt, String errorCode) {
}
