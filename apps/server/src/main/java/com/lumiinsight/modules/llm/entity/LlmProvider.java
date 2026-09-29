package com.lumiinsight.modules.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("llm_provider")
public class LlmProvider {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String protocol;
    private String baseUrl;
    private String apiKeyCipher;
    private String apiKeySuffix;
    private String extraHeaders;
    private Integer enabled;
    private Integer timeoutMs;
    private Integer maxRetry;
    @TableLogic
    private Integer deleted;
}
