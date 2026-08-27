package com.dpom.agent.common.diagnosisevent;

import java.util.List;

/**
 * Diagnosis Event 的完整来源版本信息。
 *
 * @param application   应用版本
 * @param model         模型版本
 * @param prompt        提示词版本
 * @param skills        技能版本
 * @param toolContracts 工具契约版本
 * @param source        源码版本
 * @param evidenceSchema 证据结构版本
 */
public record DiagnosisEventProvenance(ProvenanceVersion application, ProvenanceVersion model,
                                       ProvenanceVersion prompt, List<ProvenanceVersion> skills,
                                       List<ProvenanceVersion> toolContracts, ProvenanceSource source,
                                       ProvenanceVersion evidenceSchema) {

    /**
     * 防御性复制集合，避免事件创建后被调用方修改。
     */
    public DiagnosisEventProvenance {
        skills = skills == null ? null : List.copyOf(skills);
        toolContracts = toolContracts == null ? null : List.copyOf(toolContracts);
    }
}
