package com.lumiinsight.modules.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("data_source_usage")
public class DataSourceUsage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long providerId;
    private Long channelId;
    private Long projectId;
    private String platform;
    private Long runId;
    private Integer requestCount;
    private Integer itemCount;
    private BigDecimal costYuan;
    private Integer success;
    private String detail;
    private LocalDateTime createdAt;
}
