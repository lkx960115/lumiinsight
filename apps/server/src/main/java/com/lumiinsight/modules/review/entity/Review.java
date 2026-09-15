package com.lumiinsight.modules.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("review")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long importJobId;
    private String platform;
    private String productId;
    private String reviewId;
    private String content;
    private LocalDateTime reviewTime;
    private Integer likeCount;
    private String authorHash;
    private String productName;
    private String sourceUrl;
    private String rawPayload;
    private String contentHash;
    private String cleanTags;
    private String cleanReason;
    private String aspectHits;
    private String sentiment;
    private String sentimentReason;
    private BigDecimal sentimentConfidence;
    private String analyzeSource;
    private Integer counted;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
