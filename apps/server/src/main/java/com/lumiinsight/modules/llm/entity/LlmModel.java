package com.lumiinsight.modules.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("llm_model")
public class LlmModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long providerId;
    private String modelCode;
    private Integer contextLength;
    private Integer jsonMode;
    private BigDecimal inputPrice;
    private BigDecimal outputPrice;
    private Integer enabled;
    @TableLogic
    private Integer deleted;
}
