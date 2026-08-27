package com.dpom.agent.core.persistence.command;

import java.time.LocalDateTime;

/**
 * 发件箱租约获取命令。
 */
public record DiagnosisEventLeaseCommand(long id, LocalDateTime now, String leaseOwner, String leaseToken,
                                         LocalDateTime leaseExpiresAt) {
}
