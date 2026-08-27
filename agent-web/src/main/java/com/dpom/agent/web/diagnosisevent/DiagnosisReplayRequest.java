package com.dpom.agent.web.diagnosisevent;

/**
 * 经过严格字段校验的内部重放请求。
 */
public record DiagnosisReplayRequest(String eventId, String operatorRef, String reason) {
}
