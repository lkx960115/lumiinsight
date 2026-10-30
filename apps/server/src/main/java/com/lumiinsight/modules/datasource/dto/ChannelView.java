package com.lumiinsight.modules.datasource.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChannelView {
    private Long id;
    private Long providerId;
    private String platform;
    private String endpointPath;
    private String httpMethod;
    private String requestTemplate;
    private String reviewMapping;
    private String itemsPath;
    private Integer pageSize;
    private Integer supportsCompetitor;
    private Integer enabled;
}
