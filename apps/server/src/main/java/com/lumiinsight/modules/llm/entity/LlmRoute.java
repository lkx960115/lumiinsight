package com.lumiinsight.modules.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("llm_route")
public class LlmRoute {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String purposeCode;
    private Long primaryModelId;
    private Long backupModelId;
    private Integer enabled;
    @TableLogic
    private Integer deleted;
}
