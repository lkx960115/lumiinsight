package com.lumiinsight.modules.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("data_source_provider")
public class DataSourceProvider {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String protocol;
    private String baseUrl;
    private String apiKeyCipher;
    private String apiKeySuffix;
    private BigDecimal pricePerItem;
    private BigDecimal dailyBudgetYuan;
    private Integer dailyItemLimit;
    private Integer maxPagesPerRun;
    private Integer timeoutMs;
    private Integer maxRetry;
    private Integer enabled;
    private String remark;
    @TableLogic
    private Integer deleted;
}
