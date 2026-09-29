package com.lumiinsight.modules.channel;

import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.modules.importdata.PlatformNormalizer;

import java.util.Map;

public class ChannelAdapterRegistry {

    private final Map<String, ChannelAdapter> adapters;

    public ChannelAdapterRegistry(Map<String, ChannelAdapter> adapters) {
        this.adapters = adapters;
    }

    public void pull(Long projectId, String platform) {
        String code = PlatformNormalizer.normalize(platform);
        ChannelAdapter adapter = adapters.get(code);
        if (adapter == null) {
            throw BizException.of("CHANNEL_NOT_IMPLEMENTED", "未知平台: " + platform);
        }
        adapter.pull(projectId);
    }
}
