package com.dpom.agent.common.diagnosisevent;

/**
 * 投递端口返回的有界确认。
 *
 * @param outcome   稳定处理结果
 * @param errorCode 可选稳定错误码
 */
public record DeliveryAcknowledgement(DeliveryOutcome outcome, String errorCode) {
}
