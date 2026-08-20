package com.dpom.agent.alarm.governance;

import com.dpom.agent.common.alarm.AlarmSource;
import com.dpom.agent.common.alarm.SeverityLevel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 告警指纹：来源服务 + 资源标识 + 告警名称 + 严重度的稳定哈希，用作去重键。
 */
public final class AlarmFingerprint {

    private AlarmFingerprint() {
    }

    /**
     * 计算告警指纹。
     *
     * @param source     来源服务
     * @param resourceId 资源标识
     * @param alarmName  告警名称
     * @param severity   严重度
     * @return 指纹十六进制字符串
     */
    public static String of(AlarmSource source, String resourceId, String alarmName, SeverityLevel severity) {
        String material = source.name() + "|" + resourceId + "|" + alarmName + "|" + severity.name();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
