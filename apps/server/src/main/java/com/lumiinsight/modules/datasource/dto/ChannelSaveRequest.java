package com.lumiinsight.modules.datasource.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChannelSaveRequest {
    @NotBlank
    private String platform;
    @NotBlank
    private String endpointPath;
    private String httpMethod = "GET";
    private String requestTemplate;
    @NotBlank
    private String reviewMapping;
    @NotBlank
    private String itemsPath;
    private Integer pageSize = 20;
    private Integer supportsCompetitor = 1;
    private Integer enabled = 1;
}
