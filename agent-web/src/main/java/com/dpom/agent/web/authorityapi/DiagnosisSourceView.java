package com.dpom.agent.web.authorityapi;

import com.dpom.agent.core.authority.DiagnosisSourceProjection;

/** 不可变诊断源查询结果；redacted 表示响应文本相对源摘要经过遮蔽。 */
public record DiagnosisSourceView(DiagnosisSourceProjection source, boolean redacted) {
}

