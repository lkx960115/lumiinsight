package com.lumiinsight.modules.ask.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ask_history")
public class AskHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String question;
    private String answer;
    private String source;
    private String evidenceJson;
    private Long createdBy;
    private LocalDateTime createdAt;
}
