package com.dpom.agent.web.handoff;

import com.dpom.agent.common.handoff.EvidenceHandoffStore;
import com.dpom.agent.common.handoff.HandoffStoreException;

/**
 * 禁用态证据交接存储：默认装配，不产生任何外部写入。
 */
public class DisabledEvidenceHandoffStore implements EvidenceHandoffStore {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void store(String objectKey, byte[] content) {
        throw new HandoffStoreException("OBS_DISABLED", "obs transport disabled");
    }

    @Override
    public byte[] retrieve(String objectKey) {
        throw new HandoffStoreException("OBS_DISABLED", "obs transport disabled");
    }
}
