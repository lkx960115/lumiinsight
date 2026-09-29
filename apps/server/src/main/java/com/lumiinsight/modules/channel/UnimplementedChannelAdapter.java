package com.lumiinsight.modules.channel;

import com.lumiinsight.common.exception.BizException;

public class UnimplementedChannelAdapter implements ChannelAdapter {
    private final String platform;

    public UnimplementedChannelAdapter(String platform) {
        this.platform = platform;
    }

    @Override
    public String platform() {
        return platform;
    }

    @Override
    public void pull(Long projectId) {
        throw BizException.of("CHANNEL_NOT_IMPLEMENTED", "平台采集未开放，请使用 Excel/CSV 导入。projectId=" + projectId);
    }
}
