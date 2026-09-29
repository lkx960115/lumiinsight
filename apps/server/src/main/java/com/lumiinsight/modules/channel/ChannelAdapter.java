package com.lumiinsight.modules.channel;

public interface ChannelAdapter {
    String platform();

    void pull(Long projectId);
}
