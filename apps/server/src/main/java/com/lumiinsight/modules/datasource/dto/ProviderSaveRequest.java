package com.lumiinsight.modules.datasource.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProviderSaveRequest {
    @NotBlank
    private String name;
    private String protocol = "HTTP_JSON";
    @NotBlank
    private String baseUrl;
    private String apiKey;
    private BigDecimal pricePerItem = BigDecimal.ZERO;
    private BigDecimal dailyBudgetYuan = BigDecimal.ZERO;
    private Integer dailyItemLimit = 0;
    private Integer maxPagesPerRun = 20;
    private Integer timeoutMs = 120000;
    private Integer maxRetry = 1;
    private Integer enabled = 0;
    private String remark;
}
