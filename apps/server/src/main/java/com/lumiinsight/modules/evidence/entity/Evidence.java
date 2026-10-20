package com.lumiinsight.modules.evidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("evidence")
public class Evidence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long reviewId;
    private String aspectName;
    private String sentiment;
    private String quote;
    private String source;
    private LocalDateTime createdAt;
}
