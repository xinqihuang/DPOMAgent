package com.dpom.agent.common.diagnosisevent;

import java.time.OffsetDateTime;

/**
 * 受控制品引用。
 *
 * @param artifactId           制品标识
 * @param locationType         受控位置类型
 * @param locator              位置内定位符
 * @param mediaType            媒体类型
 * @param byteSize             字节数
 * @param sha256               小写 SHA-256
 * @param artifactSchemaVersion 制品结构版本
 * @param retentionClass       保留等级
 * @param createdAt            创建时间
 */
public record DiagnosisArtifactReference(String artifactId, String locationType, String locator, String mediaType,
                                         long byteSize, String sha256, String artifactSchemaVersion,
                                         String retentionClass, OffsetDateTime createdAt) {
}
