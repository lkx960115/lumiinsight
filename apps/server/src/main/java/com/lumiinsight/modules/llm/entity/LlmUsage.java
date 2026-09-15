package com.lumiinsight.modules.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_usage")
public class LlmUsage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String purposeCode;
    private Long providerId;
    private Long modelId;
    private String modelCode;
    private Long jobId;
    private Long projectId;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer success;
    private String detail;
    private LocalDateTime createdAt;
}
