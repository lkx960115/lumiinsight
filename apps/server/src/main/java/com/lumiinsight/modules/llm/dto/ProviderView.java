package com.lumiinsight.modules.llm.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderView {
    private Long id;
    private String name;
    private String protocol;
    private String baseUrl;
    private String apiKeyMasked;
    private boolean hasKey;
    private String extraHeaders;
    private Integer enabled;
    private Integer timeoutMs;
    private Integer maxRetry;
}
