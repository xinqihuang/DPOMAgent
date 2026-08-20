package com.dpom.agent.alarm.notification;

/**
 * 发送结果。
 *
 * @param success     是否成功
 * @param errorMessage 错误信息（成功时为空）
 */
public record SendOutcome(boolean success, String errorMessage) {

    /**
     * 构造成功结果。
     *
     * @return 成功
     */
    public static SendOutcome ok() {
        return new SendOutcome(true, null);
    }

    /**
     * 构造失败结果。
     *
     * @param errorMessage 错误信息
     * @return 失败
     */
    public static SendOutcome fail(String errorMessage) {
        return new SendOutcome(false, errorMessage);
    }
}
