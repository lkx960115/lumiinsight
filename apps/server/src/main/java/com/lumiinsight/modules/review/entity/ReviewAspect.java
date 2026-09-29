package com.lumiinsight.modules.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("review_aspect")
public class ReviewAspect {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long reviewId;
    private String aspectName;
    private String sentiment;
    private String reason;
    private BigDecimal confidence;
    private String source;
    private LocalDateTime createdAt;
}
