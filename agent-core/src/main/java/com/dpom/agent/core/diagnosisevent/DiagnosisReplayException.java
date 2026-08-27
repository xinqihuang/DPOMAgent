package com.dpom.agent.core.diagnosisevent;

/**
 * 内部重放的稳定业务错误。
 */
public class DiagnosisReplayException extends RuntimeException {

    /** 创建稳定重放错误。 */
    public DiagnosisReplayException(String errorCode) {
        super(errorCode);
    }
}
