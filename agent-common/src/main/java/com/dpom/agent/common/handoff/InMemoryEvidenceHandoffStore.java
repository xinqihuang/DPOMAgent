package com.dpom.agent.common.handoff;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存证据交接存储：确定性 fake adapter，仅用于测试与本地开发，不连接任何真实 OBS。
 */
public class InMemoryEvidenceHandoffStore implements EvidenceHandoffStore {

    private final ConcurrentMap<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void store(String objectKey, byte[] content) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new HandoffStoreException("INVALID_OBJECT_KEY", "objectKey required");
        }
        if (content == null) {
            throw new HandoffStoreException("EMPTY_CONTENT", "content required");
        }
        objects.put(objectKey, content.clone());
    }

    @Override
    public byte[] retrieve(String objectKey) {
        byte[] found = objects.get(objectKey);
        if (found == null) {
            throw new HandoffStoreException("OBJECT_NOT_FOUND", "object not found");
        }
        return found.clone();
    }

    /**
     * 已存储对象数量（测试/开发用）。
     *
     * @return 对象数量
     */
    public int size() {
        return objects.size();
    }
}
