package com.lumiinsight.modules.channel.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChannelShopView {
    private String platform;
    private String shopName;
    private String status;
    private long volume;
    private LocalDateTime lastSyncedAt;
}
