package com.lumiinsight.modules.review.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReviewView {
    private Long id;
    private String platform;
    private String productId;
    private String reviewId;
    private String content;
    private LocalDateTime reviewTime;
    private Integer likeCount;
    private String productName;
    private String sourceUrl;
    private String cleanTags;
    private String cleanReason;
    private String aspectHits;
    private Integer counted;
    private String sentiment;
    private String sentimentReason;
    private BigDecimal sentimentConfidence;
    private String analyzeSource;
    private List<ReviewAspectView> aspects;
}
