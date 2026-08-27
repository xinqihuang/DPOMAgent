package com.dpom.agent.core.diagnosisevent;

/**
 * Diagnosis Event 构造或验证失败。
 */
public class DiagnosisEventValidationException extends RuntimeException {

    private final String errorCode;

    /**
     * 创建稳定错误异常。
     *
     * @param errorCode 稳定错误码
     */
    public DiagnosisEventValidationException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    /**
     * 返回稳定错误码。
     *
     * @return 稳定错误码
     */
    public String errorCode() {
        return errorCode;
    }
}
