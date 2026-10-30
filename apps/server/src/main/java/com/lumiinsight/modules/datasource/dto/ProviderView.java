package com.lumiinsight.modules.datasource.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProviderView {
    private Long id;
    private String name;
    private String protocol;
    private String baseUrl;
    private String apiKeyMasked;
    private boolean hasKey;
    private BigDecimal pricePerItem;
    private BigDecimal dailyBudgetYuan;
    private Integer dailyItemLimit;
    private Integer maxPagesPerRun;
    private Integer timeoutMs;
    private Integer maxRetry;
    private Integer enabled;
    private String remark;
    private long todayItemCount;
    private BigDecimal todayCostYuan;
    private BigDecimal remainingBudgetYuan;
    private List<ChannelView> channels;
}
