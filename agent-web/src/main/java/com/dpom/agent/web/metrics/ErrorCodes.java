package com.dpom.agent.web.metrics;

import com.dpom.agent.common.codegraph.SnapshotNotFoundException;
import com.dpom.agent.common.codegraph.SnapshotNotReadyException;
import com.dpom.agent.common.llm.ModelProviderException;
import com.dpom.agent.common.llm.ModelTimeoutException;

/**
 * 稳定错误码有限白名单：只按异常类型映射，绝不使用异常类名/message 作为指标标签。
 */
public final class ErrorCodes {

    public static final String NONE = "NONE";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String PROVIDER_ERROR = "PROVIDER_ERROR";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String NOT_READY = "NOT_READY";
    public static final String ERROR = "ERROR";
    public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
    public static final String ILLEGAL_STATE = "ILLEGAL_STATE";
    public static final String EXECUTION_ERROR = "EXECUTION_ERROR";
    public static final String CAPACITY_FULL = "CAPACITY_FULL";
    public static final String RECONCILED_AFTER_RESTART = "RECONCILED_AFTER_RESTART";

    private ErrorCodes() {
    }

    /** 调查执行异常 → 稳定错误码。 */
    public static String execution(Throwable t) {
        if (t instanceof IllegalArgumentException) {
            return INVALID_ARGUMENT;
        }
        if (t instanceof IllegalStateException) {
            return ILLEGAL_STATE;
        }
        return EXECUTION_ERROR;
    }

    /** 外部适配器调用异常 → 稳定错误码（成功请用 NONE）。 */
    public static String adapter(Throwable t) {
        if (t instanceof ModelTimeoutException) {
            return TIMEOUT;
        }
        if (t instanceof ModelProviderException) {
            return PROVIDER_ERROR;
        }
        if (t instanceof SnapshotNotFoundException) {
            return NOT_FOUND;
        }
        if (t instanceof SnapshotNotReadyException) {
            return NOT_READY;
        }
        return ERROR;
    }
}
