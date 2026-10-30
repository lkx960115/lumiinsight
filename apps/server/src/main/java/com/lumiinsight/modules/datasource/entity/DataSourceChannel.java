package com.lumiinsight.modules.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("data_source_channel")
public class DataSourceChannel {
    @TableId(type = IdType.AUTO)
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
