package com.dpom.agent.common.handoff;

/**
 * 证据交接传输端口：OBS 出站/下载的抽象。核心只依赖此端口，不感知具体云厂商。
 *
 * <p>对象名由核心生成后传入；凭据、endpoint、bucket、本地路径与对象名均不进入此契约。
 * 真实 OBS SDK 集成由后续独立 Change 的适配器实现。</p>
 */
public interface EvidenceHandoffStore {

    /**
     * 是否启用真实传输。
     *
     * @return true 表示已配置且可传输，false 表示禁用
     */
    boolean isEnabled();

    /**
     * 上传证据包字节。
     *
     * @param objectKey 服务生成的对象名
     * @param content   证据包字节
     * @throws HandoffStoreException 传输失败（结构化错误码）
     */
    void store(String objectKey, byte[] content);

    /**
     * 下载证据包字节。
     *
     * @param objectKey 对象名
     * @return 证据包字节
     * @throws HandoffStoreException 对象不存在或传输失败（结构化错误码）
     */
    byte[] retrieve(String objectKey);
}
