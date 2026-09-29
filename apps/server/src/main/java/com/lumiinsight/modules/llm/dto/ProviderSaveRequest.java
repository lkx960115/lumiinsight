package com.lumiinsight.modules.llm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProviderSaveRequest {
    @NotBlank
    private String name;
    private String protocol = "openai_compat";
    @NotBlank
    private String baseUrl;
    private String apiKey;
    private String extraHeaders;
    private Integer enabled = 1;
    private Integer timeoutMs = 60000;
    private Integer maxRetry = 1;
}
